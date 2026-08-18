package com.hirewise.be.repository;

import com.hirewise.be.domain.UserAccessScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserAccessScopeRepository extends JpaRepository<UserAccessScope, Long> {

    /** RBAC layer 3: toan bo scope dang hieu luc tai thoi diem `now` cua 1
     * user (BR-RBAC-05: co the co nhieu dong DEPARTMENT - UNION khi check). */
    @Query("""
            SELECT s FROM UserAccessScope s
            WHERE s.user.id = :userId
                AND s.validFrom <= :now
                AND (s.validTo IS NULL OR s.validTo > :now)
            """)
    List<UserAccessScope> findActiveScopes(@Param("userId") Long userId, @Param("now") Instant now);

    List<UserAccessScope> findByUserId(Long userId);
}
