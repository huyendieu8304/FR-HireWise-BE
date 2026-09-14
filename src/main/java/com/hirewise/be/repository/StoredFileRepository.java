package com.hirewise.be.repository;

import com.hirewise.be.domain.StoredFile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link StoredFile} entities (metadata for a file kept on
 * Cloud Storage, or temporarily queued locally per BR-STORAGE-02 - see
 * {@code service.FileStorageService}).
 */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /**
     * Loads a file together with the storage connection needed to download it.
     * <p>
     * For callers outside a transaction (e.g. {@code OutboxDispatcher}, whose
     * scheduled poll invokes its own {@code dispatchOne} and so bypasses the
     * transactional proxy): with the lazy associations left unloaded,
     * {@code FileStorageService#downloadFile} would fail with
     * {@code LazyInitializationException}.
     */
    @EntityGraph(attributePaths = {"storageConnection", "storageConnection.integrationConnection"})
    Optional<StoredFile> findWithStorageConnectionById(Long id);
}
