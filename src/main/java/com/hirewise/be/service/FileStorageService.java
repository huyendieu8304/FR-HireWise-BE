package com.hirewise.be.service;

import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.FileStatus;
import com.hirewise.be.domain.IntegrationConnection;
import com.hirewise.be.domain.IntegrationProvider;
import com.hirewise.be.domain.OauthToken;
import com.hirewise.be.domain.StorageConnection;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.integration.CloudStorageProviderClient;
import com.hirewise.be.integration.IntegrationConnectException;
import com.hirewise.be.integration.TokenCipher;
import com.hirewise.be.repository.OauthTokenRepository;
import com.hirewise.be.repository.StorageConnectionRepository;
import com.hirewise.be.repository.StoredFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-17 step 5: stores a candidate's CV onto the Cloud Storage connected via
 * UC-07/UC-08. When that connection is {@code EXPIRED}/{@code REVOKED} (or
 * an upload attempt fails despite being {@code CONNECTED} - e.g. a
 * revoked-but-not-yet-noticed token), EX-03/BR-STORAGE-02 apply: the file is
 * held in a local pending-upload queue instead, and an internal audit entry
 * is raised for the HR Admin - the candidate's application is still
 * accepted normally either way. Recovering a queued file onto the actual
 * Cloud Storage account once reconnected is a follow-up worker, out of
 * scope for UC-17 itself.
 */
@Slf4j
@Service
public class FileStorageService {

    /** Prefix marking a {@code files.external_file_id} as not yet pushed to the real provider (BR-STORAGE-02). */
    static final String PENDING_LOCAL_PREFIX = "PENDING_LOCAL:";

    private final StorageConnectionRepository storageConnectionRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final StoredFileRepository storedFileRepository;
    private final AuditLogService auditLogService;
    private final TokenCipher tokenCipher;
    private final Clock clock;
    private final Map<IntegrationProvider, CloudStorageProviderClient> providerClients;
    private final Path pendingUploadDir;

    public FileStorageService(StorageConnectionRepository storageConnectionRepository,
                               OauthTokenRepository oauthTokenRepository,
                               StoredFileRepository storedFileRepository,
                               AuditLogService auditLogService,
                               TokenCipher tokenCipher,
                               Clock clock,
                               List<CloudStorageProviderClient> providerClients,
                               @Value("${app.file-storage.pending-upload-dir:./data/pending-cv-uploads}") String pendingUploadDir) {
        this.storageConnectionRepository = storageConnectionRepository;
        this.oauthTokenRepository = oauthTokenRepository;
        this.storedFileRepository = storedFileRepository;
        this.auditLogService = auditLogService;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
        this.providerClients = providerClients.stream()
                .collect(Collectors.toMap(CloudStorageProviderClient::provider, Function.identity()));
        this.pendingUploadDir = Path.of(pendingUploadDir);
    }

    /**
     * Stores one CV file for an application. Never throws for a Cloud
     * Storage-side problem (EX-03) - only for there being no Cloud Storage
     * connection at all, which is a genuine UC-17 precondition failure
     * (the job should never have reached Published without one, per
     * BR-APR-03 + UC-07).
     *
     * @param file       the uploaded CV (already validated: format/size, see
     *                   {@code JobApplicationService})
     * @param safeName   storage file name to use (sanitized, does not need
     *                   to match the original upload's file name)
     * @return the persisted {@link StoredFile} metadata row
     */
    @Transactional
    public StoredFile storeCv(MultipartFile file, String safeName) {
        StorageConnection connection = storageConnectionRepository.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "No Cloud Storage connection exists - a job cannot be Published (BR-APR-03) without one configured via UC-07"));

        byte[] content = readAllBytes(file);
        String checksum = sha256Hex(content);
        IntegrationConnection integrationConnection = connection.getIntegrationConnection();

        if (integrationConnection.getStatus() == ConnectionStatus.CONNECTED) {
            try {
                return uploadToProvider(connection, file, safeName, content, checksum);
            } catch (IntegrationConnectException e) {
                log.warn("CV upload to {} failed even though the connection is CONNECTED - queueing locally "
                        + "per BR-STORAGE-02: {}", connection.getProvider(), e.getMessage());
                return queueLocally(connection, file, safeName, content, checksum);
            }
        }

        // EX-03: EXPIRED (or REVOKED) - hold the file locally rather than blocking the application.
        log.warn("Cloud Storage connection is {} - queueing CV locally per BR-STORAGE-02/EX-03",
                integrationConnection.getStatus());
        return queueLocally(connection, file, safeName, content, checksum);
    }

    private StoredFile uploadToProvider(StorageConnection connection, MultipartFile file, String safeName,
                                         byte[] content, String checksum) {
        OauthToken token = oauthTokenRepository.findByIntegrationConnection_Id(connection.getIntegrationConnection().getId())
                .orElseThrow(() -> new IntegrationConnectException("No OAuth token stored for the current Cloud Storage connection"));
        String accessToken = tokenCipher.decrypt(token.getAccessTokenEncrypted());
        CloudStorageProviderClient client = providerClients.get(connection.getProvider());
        if (client == null) {
            throw new IllegalStateException("No CloudStorageProviderClient registered for provider " + connection.getProvider());
        }

        String externalFileId = client.uploadFile(accessToken, connection.getRootFolderId(), safeName,
                file.getContentType(), content);

        Instant now = Instant.now(clock);
        StoredFile storedFile = StoredFile.builder()
                .storageConnection(connection)
                .fileName(originalFileName(file, safeName))
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .externalFileId(externalFileId)
                .checksumSha256(checksum)
                .status(FileStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return storedFileRepository.save(storedFile);
    }

    private StoredFile queueLocally(StorageConnection connection, MultipartFile file, String safeName,
                                     byte[] content, String checksum) {
        String localFileName = UUID.randomUUID() + "_" + safeName;
        try {
            Files.createDirectories(pendingUploadDir);
            Files.write(pendingUploadDir.resolve(localFileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to queue CV file in the local pending-upload directory", e);
        }

        Instant now = Instant.now(clock);
        StoredFile storedFile = StoredFile.builder()
                .storageConnection(connection)
                .fileName(originalFileName(file, safeName))
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .externalFileId(PENDING_LOCAL_PREFIX + localFileName)
                .checksumSha256(checksum)
                .status(FileStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        storedFile = storedFileRepository.save(storedFile);

        // BR-STORAGE-02: internal warning for the HR Admin - the candidate is never told.
        auditLogService.record(null, "CLOUD_STORAGE_UPLOAD_QUEUED_LOCALLY", "files", String.valueOf(storedFile.getId()));
        return storedFile;
    }

    private static String originalFileName(MultipartFile file, String fallback) {
        return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : fallback;
    }

    private static byte[] readAllBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded CV file", e);
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM - a programming error, not a business one.
            throw new IllegalStateException(e);
        }
    }
}
