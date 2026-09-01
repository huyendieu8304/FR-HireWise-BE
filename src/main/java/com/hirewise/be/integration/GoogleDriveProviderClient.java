package com.hirewise.be.integration;

import com.hirewise.be.domain.IntegrationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Google Drive side of UC-07/UC-08 (OAuth 2.0 authorization code flow, plus
 * root-folder creation for BR-STORAGE-03). Scoped to
 * {@code drive.file} only - the narrowest scope that still lets the app
 * create/read the files it uploads itself, per the least-privilege spirit
 * of BR-STORAGE-01.
 */
@Slf4j
@Component
public class GoogleDriveProviderClient implements CloudStorageProviderClient {

    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_API_BASE_URL = "https://www.googleapis.com/drive/v3";
    private static final String DRIVE_UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3";
    private static final String SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String ROOT_FOLDER_NAME = "HireWise";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient tokenClient = RestClient.create();
    private final RestClient driveClient = RestClient.builder().baseUrl(DRIVE_API_BASE_URL).build();
    private final RestClient driveUploadClient = RestClient.builder().baseUrl(DRIVE_UPLOAD_BASE_URL).build();

    private final java.util.concurrent.ConcurrentHashMap<String, String> folderCache = new java.util.concurrent.ConcurrentHashMap<>();

    public GoogleDriveProviderClient(
            @Value("${app.integration.google-drive.client-id:}") String clientId,
            @Value("${app.integration.google-drive.client-secret:}") String clientSecret,
            @Value("${app.integration.google-drive.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.GOOGLE_DRIVE;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                // access_type=offline + prompt=consent: without both, Google only
                // issues a refresh_token on the FIRST-EVER consent for this
                // client_id+account, which would silently break Reconnect (UC-08).
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeAuthorizationCode(String code) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Google Drive token exchange failed", e);
        }
    }

    @Override
    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        requireConfigured();
        // Google's refresh_token grant: same token endpoint as the authorization_code
        // exchange above, no redirect_uri involved this time. Google normally does
        // NOT return a new refresh_token here - callers must keep reusing the one
        // passed in unless the response explicitly contains a fresh one.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            // Most commonly a revoked/expired refresh_token (e.g. HR Admin revoked
            // HireWise's access from their Google Account settings) - the caller
            // (CloudStorageTokenRefreshWorker) falls back to marking the connection
            // EXPIRED so UC-08's normal Reconnect flow can recover it.
            throw new IntegrationConnectException("Google Drive token refresh failed", e);
        }
    }

    @Override
    public String createRootFolder(String accessToken) {
        try {
            // First check if the root folder already exists to avoid duplicates
            String query = String.format("name='%s' and 'root' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false", 
                    escapeString(ROOT_FOLDER_NAME));
            Map<?, ?> searchResponse = driveClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/files")
                            .queryParam("q", query)
                            .queryParam("fields", "files(id)")
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(Map.class);
                    
            if (searchResponse != null) {
                java.util.List<Map<String, String>> files = (java.util.List<Map<String, String>>) searchResponse.get("files");
                if (files != null && !files.isEmpty()) {
                    return files.get(0).get("id"); // Reuse existing folder
                }
            }

            Map<String, Object> body = Map.of(
                    "name", ROOT_FOLDER_NAME,
                    "mimeType", "application/vnd.google-apps.folder");
            Map<?, ?> response = driveClient.post()
                    .uri("/files")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return response == null ? null : (String) response.get("id");
        } catch (RestClientException e) {
            // Non-fatal by design - see CloudStorageProviderClient#createRootFolder.
            log.warn("Failed to create the Google Drive root folder right after connecting: {}", e.getMessage());
            return null;
        }
    }
    
    private String getOrCreateSubfolder(String accessToken, String rootFolderId, String subfolderName) {
        if (rootFolderId == null || subfolderName == null || subfolderName.isBlank()) {
            return rootFolderId;
        }
        String currentParentId = rootFolderId;
        String[] parts = subfolderName.split("/");
        for (String part : parts) {
            if (part.isBlank()) continue;
            currentParentId = getOrCreateSingleSubfolder(accessToken, currentParentId, part);
        }
        return currentParentId;
    }

    private String getOrCreateSingleSubfolder(String accessToken, String parentFolderId, String folderName) {
        String cacheKey = parentFolderId + "_" + folderName;
        if (folderCache.containsKey(cacheKey)) {
            return folderCache.get(cacheKey);
        }
        
        try {
            String query = String.format("name='%s' and '%s' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false",
                    escapeString(folderName), parentFolderId);
            Map<?, ?> searchResponse = driveClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/files")
                            .queryParam("q", query)
                            .queryParam("fields", "files(id)")
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(Map.class);
                    
            if (searchResponse != null) {
                java.util.List<Map<String, String>> files = (java.util.List<Map<String, String>>) searchResponse.get("files");
                if (files != null && !files.isEmpty()) {
                    String folderId = files.get(0).get("id");
                    folderCache.put(cacheKey, folderId);
                    return folderId;
                }
            }
            
            // Not found, create it
            Map<String, Object> body = Map.of(
                    "name", folderName,
                    "parents", java.util.List.of(parentFolderId),
                    "mimeType", "application/vnd.google-apps.folder");
            Map<?, ?> response = driveClient.post()
                    .uri("/files")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
                    
            if (response != null && response.containsKey("id")) {
                String folderId = (String) response.get("id");
                folderCache.put(cacheKey, folderId);
                return folderId;
            }
        } catch (RestClientException e) {
            log.warn("Failed to get or create subfolder {} in Google Drive: {}", folderName, e.getMessage());
        }
        return parentFolderId; // Fallback to parent folder if creation fails
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Override
    public String uploadFile(String accessToken, String rootFolderId, String subfolderName, String fileName, String mimeType, byte[] content) {
        try {
            String targetFolderId = getOrCreateSubfolder(accessToken, rootFolderId, subfolderName);
            
            // Step 1/2: create the file's metadata (name + parent folder) - Drive's simple
            // "multipart in one call" upload is more fiddly to build by hand than doing
            // metadata-create then media-upload as two plain requests.
            Map<String, Object> metadata = targetFolderId != null
                    ? Map.of("name", fileName, "parents", java.util.List.of(targetFolderId))
                    : Map.of("name", fileName);
            Map<?, ?> created = driveClient.post()
                    .uri("/files")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(metadata)
                    .retrieve()
                    .body(Map.class);
            String fileId = created == null ? null : (String) created.get("id");
            if (fileId == null) {
                throw new IntegrationConnectException("Google Drive did not return a file id after metadata create");
            }

            // Step 2/2: upload the actual bytes onto the file just created.
            MediaType contentType = mimeType != null && !mimeType.isBlank()
                    ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM;
            driveUploadClient.patch()
                    .uri("/files/{id}?uploadType=media", fileId)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.setContentType(contentType);
                    })
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
            return fileId;
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Google Drive file upload failed", e);
        }
    }

    @Override
    public byte[] downloadFile(String accessToken, String externalFileId) {
        try {
            return driveClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/files/{id}")
                            .queryParam("alt", "media")
                            .build(externalFileId))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Failed to download file from Google Drive: " + externalFileId, e);
        }
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IntegrationConnectException(
                    "Google Drive integration is not configured (app.integration.google-drive.client-id/client-secret)");
        }
    }

    @Override
    public String getViewUrl(String accessToken, String externalFileId) {
        try {
            Map<?, ?> response = driveClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/files/{id}")
                            .queryParam("fields", "webViewLink")
                            .build(externalFileId))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(Map.class);
            if (response == null || !response.containsKey("webViewLink")) {
                throw new IntegrationConnectException(
                        "Google Drive did not return a webViewLink for file " + externalFileId);
            }
            return (String) response.get("webViewLink");
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Failed to get Google Drive view URL for file " + externalFileId, e);
        }
    }
}
