package com.hirewise.be.repository;

import com.hirewise.be.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link UserSession} entities - the session/token registry
 * backing UC-01 logout and BR-AUTH-04 (revoke-all-sessions-on-block).
 */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedAtIsNull(Long userId);

    /**
     * BR-AUTH-04: revokes every currently-active session of a user in one
     * statement (used when an HR Admin blocks/disables an account).
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
