package com.hirewise.be.repository;

import com.hirewise.be.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link StoredFile} entities (metadata for a file kept on
 * Cloud Storage, or temporarily queued locally per BR-STORAGE-02 - see
 * {@code service.FileStorageService}).
 */
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
}
