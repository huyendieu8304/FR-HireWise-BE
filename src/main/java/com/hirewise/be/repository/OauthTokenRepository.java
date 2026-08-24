package com.hirewise.be.repository;

import com.hirewise.be.domain.OauthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link OauthToken} entities.
 */
public interface OauthTokenRepository extends JpaRepository<OauthToken, Long> {

    /**
     * @param integrationConnectionId id of the owning {@code integration_connections} row
     * @return the token row for that connection, if one has been stored
     */
    Optional<OauthToken> findByIntegrationConnection_Id(Long integrationConnectionId);
}
