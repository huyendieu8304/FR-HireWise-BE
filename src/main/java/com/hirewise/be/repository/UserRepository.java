package com.hirewise.be.repository;

import com.hirewise.be.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(String keycloakId);

    boolean existsByKeycloakId(String keycloakId);

    boolean existsByEmail(String email);

    /**
     * Returns only the account status for the given Keycloak id, without
     * loading the full user entity.
     *
     * @param keycloakId Keycloak subject id of the user
     * @return the user's status, or empty if no user matches
     */
    @Query("SELECT u.status FROM User u WHERE u.keycloakId = :keycloakId")
    Optional<com.hirewise.be.domain.UserStatus> findStatusByKeycloakId(@Param("keycloakId") String keycloakId);
}
