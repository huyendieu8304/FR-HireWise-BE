package com.hirewise.be.repository;

import com.hirewise.be.security.token.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link ActivationToken} entities backing the EM-01
 * activation link (UC-02).
 */
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {
}
