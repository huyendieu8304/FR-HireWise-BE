package com.hirewise.be.config;

import com.hirewise.be.domain.AuthIdentity;
import com.hirewise.be.domain.AuthProvider;
import com.hirewise.be.domain.Role;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.domain.UserRole;
import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.logging.LogMaskUtils;
import com.hirewise.be.repository.AuthIdentityRepository;
import com.hirewise.be.repository.RoleRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Solves the "chicken and egg" problem left after removing Keycloak:
 * {@code USER_CREATE}/{@code ROLE_ASSIGN} require an existing HR_ADMIN, but
 * without an external Identity Provider there is no separate admin console
 * to create the very first account from anymore.
 * <p>
 * On every application startup, if {@code app.bootstrap.admin-email} and
 * {@code app.bootstrap.admin-password} are both set AND no user with that
 * email exists yet, this creates exactly one ACTIVE user with a LOCAL
 * {@code auth_identities} row (Argon2id-hashed password), the HR_ADMIN role
 * and a SYSTEM access scope with {@code canWrite=true} - fully usable for
 * login immediately, no activation email needed.
 *
 * <p>
 * Deliberately idempotent (checked by email existence) so it is safe to
 * leave configured across restarts; operators should still unset
 * {@code app.bootstrap.admin-password} (env var {@code BOOTSTRAP_ADMIN_PASSWORD})
 * once the first real HR_ADMIN login has happened, in {@code prod} in
 * particular, so the well-known bootstrap credential isn't left indefinitely
 * valid/discoverable in config.
 */
@Slf4j
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAccessScopeRepository userAccessScopeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminFullName;

    public BootstrapAdminInitializer(UserRepository userRepository,
                                      AuthIdentityRepository authIdentityRepository,
                                      UserRoleRepository userRoleRepository,
                                      UserAccessScopeRepository userAccessScopeRepository,
                                      RoleRepository roleRepository,
                                      PasswordEncoder passwordEncoder,
                                      Clock clock,
                                      @Value("${app.bootstrap.admin-email:}") String adminEmail,
                                      @Value("${app.bootstrap.admin-password:}") String adminPassword,
                                      @Value("${app.bootstrap.admin-full-name:Bootstrap HR Admin}") String adminFullName) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.userRoleRepository = userRoleRepository;
        this.userAccessScopeRepository = userAccessScopeRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFullName = adminFullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            // Not configured (e.g. every restart in prod after the first HR_ADMIN
            // exists) - intentionally silent, this is the expected steady state.
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        Role hrAdminRole = roleRepository.findByCode("HR_ADMIN").orElse(null);
        if (hrAdminRole == null) {
            // Flyway V2 (seeds the 5 system roles) hasn't run yet, or the roles
            // table is unexpectedly empty - nothing safe to do, try again next boot.
            log.warn("Skipping HR_ADMIN bootstrap: role code HR_ADMIN not found in roles table");
            return;
        }

        Instant now = Instant.now(clock);
        User admin = User.builder()
                .email(adminEmail)
                .fullName(adminFullName)
                .status(UserStatus.ACTIVE)
                .lastAuthenticatedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(admin);

        AuthIdentity identity = AuthIdentity.builder()
                .user(admin)
                .provider(AuthProvider.LOCAL)
                .providerSubject(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        authIdentityRepository.save(identity);

        UserRole userRole = UserRole.builder()
                .user(admin)
                .role(hrAdminRole)
                .validFrom(now)
                .build();
        userRoleRepository.save(userRole);

        UserAccessScope systemScope = UserAccessScope.builder()
                .user(admin)
                .scopeType(ScopeType.SYSTEM)
                .includeSubDepartments(true)
                .canWrite(true)
                .validFrom(now)
                .build();
        userAccessScopeRepository.save(systemScope);

        log.warn("Bootstrapped initial HR_ADMIN account (email={}) - unset app.bootstrap.admin-password " +
                        "(env BOOTSTRAP_ADMIN_PASSWORD) once you have confirmed you can log in with it.",
                LogMaskUtils.maskEmail(adminEmail));
    }
}
