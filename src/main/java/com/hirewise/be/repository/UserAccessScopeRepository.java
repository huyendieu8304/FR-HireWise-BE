package com.hirewise.be.repository;

import com.hirewise.be.domain.UserAccessScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for {@link UserAccessScope} entities.
 */
public interface UserAccessScopeRepository extends JpaRepository<UserAccessScope, Long> {

    /**
     * RBAC layer 3: returns all access scopes currently in effect for a
     * user as of {@code now}.
     * <p>
     * BR-RBAC-05: a user may have several DEPARTMENT scope rows at once,
     * so callers must combine/union the results rather than expecting a
     * single scope.
     *
     * @param userId id of the user whose active scopes are resolved
     * @param now    point in time to evaluate scope validity against
     * @return scopes valid at {@code now}
     */
    @Query("""
            SELECT s FROM UserAccessScope s
            WHERE s.user.id = :userId
                AND s.validFrom <= :now
                AND (s.validTo IS NULL OR s.validTo > :now)
            """)
    List<UserAccessScope> findActiveScopes(@Param("userId") Long userId, @Param("now") Instant now);

    List<UserAccessScope> findByUserId(Long userId);
}
