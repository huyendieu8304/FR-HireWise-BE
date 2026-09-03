package com.hirewise.be.repository;

import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationConnection;
import com.hirewise.be.domain.IntegrationProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link IntegrationConnection} entities.
 */
public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, Long> {

    /**
     * UC-07/UC-08: the MVP assumes at most one active connection per
     * (provider, purpose) pair (a single shared company account) - see the
     * "Assumptions" field of UC-07. Ordered by id descending purely to
     * pick a deterministic single row if more than one ever exists.
     *
     * @param provider the Cloud Storage provider
     * @param purpose  e.g. {@code "CLOUD_STORAGE"}
     * @return the connection for this provider/purpose, if one has ever been created
     */
    Optional<IntegrationConnection> findFirstByProviderAndPurposeOrderByIdDesc(IntegrationProvider provider, String purpose);

    /**
     * UC-08: the current connection used for Cloud Storage, regardless of
     * which provider it is - only one is ever active at a time in the MVP.
     *
     * @param purpose e.g. {@code "CLOUD_STORAGE"}
     * @param status  e.g. {@code ConnectionStatus.CONNECTED}
     * @return the most recent connection for this purpose, if any
     */
    Optional<IntegrationConnection> findFirstByPurposeAndStatusOrderByIdDesc(String purpose, ConnectionStatus status);
}
