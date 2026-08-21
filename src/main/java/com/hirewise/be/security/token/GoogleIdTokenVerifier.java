package com.hirewise.be.security.token;

import com.hirewise.be.exception.InvalidCredentialsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Component;

/**
 * verifies the ID token the frontend obtained directly from Google Identity Services and returns its claims (sub,
 * email, email_verified, name) once they check out.
 * <p>
 * The decoder is built LAZILY (on first actual login attempt) rather than
 * as an eager {@code @Bean}, because {@link JwtDecoders#fromIssuerLocation}
 * makes a network call to Google's OpenID discovery document at
 * construction time - doing that during application startup would make the
 * whole app fail to boot whenever Google/the network is unreachable, even
 * for deployments that never use Google SSO.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final String expectedAudience;
    private volatile JwtDecoder googleJwtDecoder;

    public GoogleIdTokenVerifier(@Value("${app.google.client-id:}") String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    /**
     * Verifies the given Google-issued ID token.
     *
     * @param idToken raw ID token from Google Identity Services
     * @return the verified token's claims
     * @throws InvalidCredentialsException if Google SSO is not configured, or the
     *      token's signature/issuer/audience/expiry don't check out
     */
    public Jwt verify(String idToken) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            log.error("Google SSO is not configured (app.google.client-id) - rejecting Google login attempt");
            throw new InvalidCredentialsException();
        }
        try {
            Jwt jwt = decoder().decode(idToken);
            String audience = jwt.getClaimAsString("aud");
            if (!expectedAudience.equals(audience)) {
                log.warn("Google ID token audience mismatch (expected client id does not match token 'aud')");
                throw new InvalidCredentialsException();
            }
            return jwt;
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            throw new InvalidCredentialsException();
        }
    }

    private JwtDecoder decoder() {
        JwtDecoder local = googleJwtDecoder;
        if (local == null) {
            synchronized (this) {
                if (googleJwtDecoder == null) {
                    googleJwtDecoder = JwtDecoders.fromIssuerLocation(GOOGLE_ISSUER);
                }
                local = googleJwtDecoder;
            }
        }
        return local;
    }
}
