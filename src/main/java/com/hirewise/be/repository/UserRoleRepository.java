package com.hirewise.be.repository;

import com.hirewise.be.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for {@link UserRole} entities.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Returns the role codes currently in effect for a user as of
     * {@code now}. A user may hold several roles at the same time.
     *
     * @param userId id of the user whose active roles are resolved
     * @param now    point in time to evaluate role validity against
     * @return codes of the roles valid at {@code now}
     */
    @Query("""
            SELECT ur.role.code FROM UserRole ur
            WHERE ur.user.id = :userId
                AND ur.validFrom <= :now
                AND (ur.validTo IS NULL OR ur.validTo > :now)
            """)
    List<String> findActiveRoleCodes(@Param("userId") Long userId, @Param("now") Instant now);

    List<UserRole> findByUserId(Long userId);
}
