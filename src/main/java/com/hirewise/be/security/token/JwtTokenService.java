package com.hirewise.be.security.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues our own self-signed access tokens (see {@code config.SecurityConfig}
 * for the {@link JwtEncoder}/{@code JwtDecoder} bean setup) - HireWise no
 * longer delegates identity to an external IdP, so this app is both the
 * token issuer AND the resource server.
 * <p>
 * Deliberately keeps the claim set minimal: {@code sub} (internal userId)
 * and {@code sid} (the {@code user_sessions} row backing this token, for
 * revocation - see {@link SessionRegistryService}) are the only claims RBAC
 * actually relies on. {@code email}/{@code name} are included purely for
 * convenience (cheap display data for {@code GET /api/me}) - NOT roles,
 * which are always re-read fresh from the DB (see {@link ActiveRolesService}).
 */
@Component
public class JwtTokenService {

    public static final String ISSUER = "hirewise-be";

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration accessTokenTtl;

    public JwtTokenService(JwtEncoder jwtEncoder,
                            Clock clock,
                            @Value("${app.jwt.access-token-ttl-seconds:28800}") long accessTokenTtlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
    }

    /** access token/session default lifetime, in seconds (8h). */
    public long accessTokenTtlSeconds() {
        return accessTokenTtl.getSeconds();
    }

    /**
     * Issues a signed access token for the given user/session.
     *
     * @param userId    internal id of the authenticated user - becomes the {@code sub} claim
     * @param sessionId id of the backing {@code user_sessions} row - becomes the {@code sid} claim
     * @param email     the user's email, embedded for cheap display (not trusted for authz)
     * @param fullName  the user's display name, embedded for cheap display (not trusted for authz)
     * @return the encoded JWT access token
     */
    public String issueAccessToken(Long userId, UUID sessionId, String email, String fullName) {
        Instant now = Instant.now(clock);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(String.valueOf(userId))
                .claim("sid", sessionId.toString())
                .claim("email", email)
                .claim("name", fullName)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
