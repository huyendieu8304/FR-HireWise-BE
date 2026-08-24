package com.hirewise.be.repository;

import com.hirewise.be.domain.StorageConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link StorageConnection} entities.
 */
public interface StorageConnectionRepository extends JpaRepository<StorageConnection, Long> {

    /**
     * @param integrationConnectionId id of the owning {@code integration_connections} row
     * @return the storage connection row for that connection, if one has been stored
     */
    Optional<StorageConnection> findByIntegrationConnection_Id(Long integrationConnectionId);

    /**
     * UC-08: the MVP has at most one Cloud Storage connection configured at
     * a time - this is what backs the status screen.
     *
     * @return the most recently created storage connection, if any exists
     */
    Optional<StorageConnection> findFirstByOrderByIdDesc();
}
