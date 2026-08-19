package com.hirewise.be.repository;

import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Returns only the account status for the given internal user id,
     * without loading the full user entity - used by RBAC layer 1
     * (Authentication Freshness, BR-AUTH-07).
     *
     * @param userId internal id of the user
     * @return the user's status, or empty if no user matches
     */
    @Query("SELECT u.status FROM User u WHERE u.id = :userId")
    Optional<UserStatus> findStatusById(@Param("userId") Long userId);
}
