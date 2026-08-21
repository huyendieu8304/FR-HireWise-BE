package com.hirewise.be.repository;

import com.hirewise.be.domain.AuthIdentity;
import com.hirewise.be.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link AuthIdentity} entities
 */
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByProviderAndProviderSubjectIgnoreCase(AuthProvider provider, String providerSubject);

    List<AuthIdentity> findByUserId(Long userId);

    Optional<AuthIdentity> findByUserIdAndProvider(Long userId, AuthProvider provider);
}
