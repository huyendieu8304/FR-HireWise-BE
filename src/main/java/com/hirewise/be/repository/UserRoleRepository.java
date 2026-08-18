package com.hirewise.be.repository;

import com.hirewise.be.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** Ma role (code) dang hieu luc tai thoi diem `now` cua 1 user - 1 user
     * co the giu nhieu role dong thoi. */
    @Query("""
            SELECT ur.role.code FROM UserRole ur
            WHERE ur.user.id = :userId
                AND ur.validFrom <= :now
                AND (ur.validTo IS NULL OR ur.validTo > :now)
            """)
    List<String> findActiveRoleCodes(@Param("userId") Long userId, @Param("now") Instant now);

    List<UserRole> findByUserId(Long userId);
}
