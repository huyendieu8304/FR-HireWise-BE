package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The Cloud Storage-specific side of an {@link IntegrationConnection}
 * (UC-07), one-to-one with it. Deliberately has no {@code status} column of
 * its own - callers read {@code getIntegrationConnection().getStatus()} so
 * connection health can never drift out of sync between the two tables.
 */
@Entity
@Table(name = "storage_connections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "storage_connection_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_connection_id", nullable = false, unique = true)
    private IntegrationConnection integrationConnection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationProvider provider;

    /** BR-STORAGE-03: root folder created on connect; per-application subfolders nest under it. */
    @Column(name = "root_folder_id", length = 255)
    private String rootFolderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
