package com.hirewise.be.service;

import com.hirewise.be.security.token.ActivationToken;
import com.hirewise.be.security.token.ActivationTokenPurpose;
import com.hirewise.be.authorization.RolePermissionCache;
import com.hirewise.be.domain.AuthIdentity;
import com.hirewise.be.domain.AuthProvider;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserSession;
import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.dto.request.ActivateAccountRequestDto;
import com.hirewise.be.dto.request.LoginRequestDto;
import com.hirewise.be.dto.request.RefreshTokenRequestDto;
import com.hirewise.be.dto.response.AccessTokenResponseDto;
import com.hirewise.be.dto.response.CurrentUserResponseDto;
import com.hirewise.be.dto.response.LoginResponseDto;
import com.hirewise.be.exception.AccountLockedException;
import com.hirewise.be.exception.AccountNotActivatedException;
import com.hirewise.be.exception.AccountNotActiveException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.InvalidCredentialsException;
import com.hirewise.be.exception.InvalidTokenException;
import com.hirewise.be.logging.LogMaskUtils;
import com.hirewise.be.repository.ActivationTokenRepository;
import com.hirewise.be.repository.AuthIdentityRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserSessionRepository;
import com.hirewise.be.security.ActiveRolesService;
import com.hirewise.be.security.token.GoogleIdTokenVerifier;
import com.hirewise.be.security.token.JwtTokenService;
import com.hirewise.be.security.token.OpaqueTokenUtil;
import com.hirewise.be.security.SessionRegistryService;
import com.hirewise.be.security.UserDirectoryService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the whole local-auth lifecycle
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    AuthIdentityRepository authIdentityRepository;
    UserSessionRepository userSessionRepository;
    ActivationTokenRepository activationTokenRepository;
    OutboxEventPublisher outboxEventPublisher;
    JwtTokenService jwtTokenService;
    PasswordEncoder passwordEncoder;
    GoogleIdTokenVerifier googleIdTokenVerifier;
    UserDirectoryService userDirectoryService;
    SessionRegistryService sessionRegistryService;
    ActiveRolesService activeRolesService;
    RolePermissionCache rolePermissionCache;
    Clock clock;

    int lockoutMaxAttempts;
    long lockoutWindowMinutes;
    long lockoutDurationMinutes;
    long refreshTokenTtlDays;

    public AuthService(UserRepository userRepository,
                        AuthIdentityRepository authIdentityRepository,
                        UserSessionRepository userSessionRepository,
                        ActivationTokenRepository activationTokenRepository,
                        OutboxEventPublisher outboxEventPublisher,
                        JwtTokenService jwtTokenService,
                        PasswordEncoder passwordEncoder,
                        GoogleIdTokenVerifier googleIdTokenVerifier,
                        UserDirectoryService userDirectoryService,
                        SessionRegistryService sessionRegistryService,
                        ActiveRolesService activeRolesService,
                        RolePermissionCache rolePermissionCache,
                        Clock clock,
                        @Value("${app.auth.lockout-max-attempts:5}") int lockoutMaxAttempts,
                        @Value("${app.auth.lockout-window-minutes:15}") long lockoutWindowMinutes,
                        @Value("${app.auth.lockout-duration-minutes:15}") long lockoutDurationMinutes,
                        @Value("${app.auth.refresh-token-ttl-days:7}") long refreshTokenTtlDays) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.userDirectoryService = userDirectoryService;
        this.sessionRegistryService = sessionRegistryService;
        this.activeRolesService = activeRolesService;
        this.rolePermissionCache = rolePermissionCache;
        this.clock = clock;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutWindowMinutes = lockoutWindowMinutes;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request, String ipAddress, String userAgent) {
        String email = request.getEmail().trim();
        Optional<AuthIdentity> identityOpt = authIdentityRepository
                .findByProviderAndProviderSubjectIgnoreCase(AuthProvider.LOCAL, email);

        // unknown email - respond identically to "wrong password" below so the
        // endpoint never reveals whether an email is registered.
        if (identityOpt.isEmpty()) {
            log.warn("Login failed - unknown LOCAL identity for email={}", LogMaskUtils.maskEmail(email));
            throw new InvalidCredentialsException();
        }
        AuthIdentity identity = identityOpt.get();
        Instant now = Instant.now(clock);

        // account currently locked out.
        if (identity.getLockedUntil() != null && identity.getLockedUntil().isAfter(now)) {
            log.warn("Login rejected - account locked until {} for email={}", identity.getLockedUntil(), LogMaskUtils.maskEmail(email));
            throw new AccountLockedException();
        }

        boolean passwordOk = identity.getPasswordHash() != null
                && passwordEncoder.matches(request.getPassword(), identity.getPasswordHash());
        if (!passwordOk) {
            registerFailedAttempt(identity, ipAddress, now);
            throw new InvalidCredentialsException();
        }

        // Password matched - reset the failure counter.
        identity.setFailedLoginAttempts(0);
        identity.setLockedUntil(null);
        identity.setUpdatedAt(now);
        authIdentityRepository.save(identity);

        User user = identity.getUser();
        // account must be ACTIVE - not yet activated (INVITED) and not Blocked/Disabled are both rejected
        // different messages so the user knows what to do next.
        validateUserStatus(user);

        user.setLastAuthenticatedAt(now);
        userRepository.save(user);

        return issueLoginResponse(user, ipAddress, userAgent, now);
    }

    @Transactional
    public LoginResponseDto loginWithGoogle(String googleIdToken, String ipAddress, String userAgent) {
        Jwt googleToken = googleIdTokenVerifier.verify(googleIdToken);
        String googleSubject = googleToken.getSubject();
        String email = googleToken.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(googleToken.getClaim("email_verified"));

        Optional<AuthIdentity> existingGoogleIdentity =
                authIdentityRepository.findByProviderAndProviderSubjectIgnoreCase(AuthProvider.GOOGLE, googleSubject);

        User user;
        Instant now = Instant.now(clock);
        if (existingGoogleIdentity.isPresent()) {
            user = existingGoogleIdentity.get().getUser();
            validateUserStatus(user);
        } else {
            // First time this Google account signs in - only link it to an EXISTING HireWise
            // account matched by verified email (precondition: "tai khoan da duoc HR Admin tao"
            // - Google SSO never auto-provisions a brand new account).
            if (!emailVerified || email == null) {
                throw new InvalidCredentialsException();
            }
            user = userRepository.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);
            validateUserStatus(user);

            AuthIdentity newIdentity = AuthIdentity.builder()
                    .user(user)
                    .provider(AuthProvider.GOOGLE)
                    .providerSubject(googleSubject)
                    .failedLoginAttempts(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            authIdentityRepository.save(newIdentity);
            log.info("Linked new GOOGLE auth identity to user {}", user.getId());
        }

        user.setLastAuthenticatedAt(now);
        userRepository.save(user);
        return issueLoginResponse(user, ipAddress, userAgent, now);
    }

    private static void validateUserStatus(User user) {
        if (user.getStatus() == UserStatus.INVITED) {
            throw new AccountNotActivatedException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
    }

    /** revokes the session tied to the caller's current access token. */
    @Transactional
    public void logout(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        userSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now(clock));
                userSessionRepository.save(session);
            }
        });
        // Evict immediately so the very next request with this (still cryptographically
        // valid) access token is rejected by AuthenticationFreshnessFilter, not just after
        // the cache TTL lapses.
        sessionRegistryService.evict(sessionId);
    }

    /** exchanges a still-valid refresh token for a new access token. */
    @Transactional
    public AccessTokenResponseDto refresh(RefreshTokenRequestDto request) {
        OpaqueTokenUtil.Parts parts = OpaqueTokenUtil.decode(request.getRefreshToken());
        if (parts == null) {
            throw new InvalidTokenException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }
        UserSession session = userSessionRepository.findById(parts.id())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_OR_EXPIRED_TOKEN));

        Instant now = Instant.now(clock);
        boolean valid = session.getRevokedAt() == null
                && session.getExpiresAt().isAfter(now)
                && passwordEncoder.matches(parts.secret(), session.getRefreshTokenHash());
        if (!valid) {
            throw new InvalidTokenException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }

        User user = session.getUser();
        // Defense in depth: this endpoint is intentionally permitAll (no access token
        // presented yet), so it must re-check BR-AUTH-07 itself instead of relying on
        // AuthenticationFreshnessFilter.
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        session.setLastUsedAt(now);
        userSessionRepository.save(session);

        String accessToken = jwtTokenService.issueAccessToken(user.getId(), session.getSessionId(), user.getEmail(), user.getFullName());
        return AccessTokenResponseDto.builder()
                .accessToken(accessToken)
                .expiresIn(jwtTokenService.accessTokenTtlSeconds())
                .tokenType("Bearer")
                .build();
    }

    /** sets the password for an Invited account and activates it. */
    @Transactional
    public void activate(ActivateAccountRequestDto request) {
        OpaqueTokenUtil.Parts parts = OpaqueTokenUtil.decode(request.getToken());
        if (parts == null) {
            throw new InvalidTokenException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }
        ActivationToken token = activationTokenRepository.findById(parts.id())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_OR_EXPIRED_TOKEN));

        Instant now = Instant.now(clock);
        boolean valid = token.getPurpose() == ActivationTokenPurpose.ACTIVATION
                && token.getUsedAt() == null
                && token.getExpiresAt().isAfter(now)
                && passwordEncoder.matches(parts.secret(), token.getTokenHash());
        if (!valid) {
            throw new InvalidTokenException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }

        User user = token.getUser();
        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
                .orElseGet(() -> AuthIdentity.builder()
                        .user(user)
                        .provider(AuthProvider.LOCAL)
                        .providerSubject(user.getEmail())
                        .failedLoginAttempts(0)
                        .createdAt(now)
                        .build());
        identity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        identity.setFailedLoginAttempts(0);
        identity.setLockedUntil(null);
        identity.setUpdatedAt(now);
        authIdentityRepository.save(identity);

        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(now);
        userRepository.save(user);

        token.setUsedAt(now);
        activationTokenRepository.save(token);

        userDirectoryService.evict(user.getId());
        log.info("Activated account for user {}", user.getId());
    }

    //record fail attempt for an user account
    private void registerFailedAttempt(AuthIdentity identity, String ipAddress, Instant now) {
        // Sliding window approximated by "reset the counter if the previous failure was
        // longer than the lockout window ago" (tracked via updatedAt) - see AuthIdentity
        // javadoc for the simplification rationale.
        boolean windowExpired = identity.getUpdatedAt() != null
                && Duration.between(identity.getUpdatedAt(), now).toMinutes() >= lockoutWindowMinutes;
        int attempts = windowExpired ? 1 : identity.getFailedLoginAttempts() + 1;
        identity.setFailedLoginAttempts(attempts);
        identity.setUpdatedAt(now);

        if (attempts >= lockoutMaxAttempts) {
            identity.setLockedUntil(now.plus(Duration.ofMinutes(lockoutDurationMinutes)));
            User user = identity.getUser();
            outboxEventPublisher.publish(OutboxEventType.SECURITY_ALERT_EMAIL,
                    OutboxPayloads.securityAlertEmail(user.getEmail(), user.getFullName(), ipAddress));
            log.warn("Account locked for {} minutes after {} failed attempts, email={}",
                    lockoutDurationMinutes, attempts, LogMaskUtils.maskEmail(identity.getProviderSubject()));
        }
        authIdentityRepository.save(identity);
    }

    private LoginResponseDto issueLoginResponse(User user, String ipAddress, String userAgent, Instant now) {
        UUID sessionId = UUID.randomUUID();
        String refreshSecret = OpaqueTokenUtil.newSecret();
        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .user(user)
                .refreshTokenHash(passwordEncoder.encode(refreshSecret))
                .userAgent(truncate(userAgent, 255))
                .ipAddress(truncate(ipAddress, 64))
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plus(Duration.ofDays(refreshTokenTtlDays)))
                .build();
        userSessionRepository.save(session);

        String accessToken = jwtTokenService.issueAccessToken(user.getId(), sessionId, user.getEmail(), user.getFullName());
        String refreshToken = OpaqueTokenUtil.encode(sessionId, refreshSecret);

        Set<String> roles = activeRolesService.rolesOf(user.getId());
        //get permission of current user role(s) - same as role_permissions that AccessControlService use
        Set<String> permissions = roles.stream()
                .flatMap(role -> rolePermissionCache.permissionsOf(role).keySet().stream())
                .collect(Collectors.toSet());
        CurrentUserResponseDto profile = CurrentUserResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .permissions(permissions)
                .build();

        log.info("Login succeeded for user {}", user.getId());
        return LoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenService.accessTokenTtlSeconds())
                .tokenType("Bearer")
                .user(profile)
                .build();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
