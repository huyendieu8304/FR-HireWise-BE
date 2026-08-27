package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata for one file kept on Cloud Storage (UC-17 CV upload). Binary
 * content never lives in this database - {@link #externalFileId} is the
 * provider-side identifier used to fetch the actual bytes, and
 * {@link #checksumSha256} lets callers detect a changed/duplicate upload.
 * Mapped to table {@code files}; named {@code StoredFile} in Java to avoid
 * clashing with {@code java.io.File}.
 */
@Entity
@Table(name = "files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storage_connection_id", nullable = false)
    private StorageConnection storageConnection;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Provider-side id used to fetch the file back; unique per storage connection. */
    @Column(name = "external_file_id", nullable = false, length = 255)
    private String externalFileId;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
