package com.hirewise.be.config;

import com.hirewise.be.security.AuthenticationFreshnessFilter;
import com.hirewise.be.security.CustomAccessDeniedHandler;
import com.hirewise.be.security.CustomAuthenticationEntryPoint;
import com.hirewise.be.security.SessionRegistryService;
import com.hirewise.be.security.UserContextMdcFilter;
import com.hirewise.be.security.UserDirectoryService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Configures Spring Security. HireWise manages its OWN accounts (Spring
 * Security + the {@code users}/{@code auth_identities} tables in the SAME
 * business database) instead of delegating to an external Identity
 * Provider - there is no more Keycloak.
 * <p>
 * The app is both the token ISSUER (see {@code security.token.JwtTokenService},
 * {@code controller.AuthController}) and the resource server that verifies
 * its own tokens - it still plugs into Spring Security's OAuth2 Resource
 * Server machinery ({@code oauth2ResourceServer().jwt(...)}) for that
 * verification step, just with a {@link JwtDecoder}/{@link JwtEncoder} pair
 * backed by a locally-held HMAC secret ({@code app.jwt.secret}) instead of
 * an external issuer's JWKS endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final UserDirectoryService userDirectoryService;
    private final SessionRegistryService sessionRegistryService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(CustomAuthenticationEntryPoint authenticationEntryPoint,
                           CustomAccessDeniedHandler accessDeniedHandler,
                           UserDirectoryService userDirectoryService,
                           SessionRegistryService sessionRegistryService,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.userDirectoryService = userDirectoryService;
        this.sessionRegistryService = sessionRegistryService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    /**
     * Derives the raw HMAC key bytes used to sign/verify our own access
     * tokens from {@code app.jwt.secret} (base64, must decode to >= 32
     * bytes for HS256). Falls back to a random per-boot key ONLY when the
     * property is left blank, so local/dev runs still work out of the box -
     * every other profile MUST set {@code JWT_SECRET} explicitly (see
     * .env.example), otherwise all previously-issued tokens/sessions become
     * invalid on every restart.
     */
    @Bean
    public SecretKeySpec jwtSigningKey(@Value("${app.jwt.secret:}") String base64Secret) {
        byte[] keyBytes;
        if (base64Secret == null || base64Secret.isBlank()) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
        } else {
            keyBytes = Base64.getDecoder().decode(base64Secret);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes (256 bits) for HS256");
            }
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKeySpec jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** local passwords (and refresh/activation token secrets) are hashed with BCrypt. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Builds the HTTP security rule chain applied to every incoming
     * request.
     * <p>
     * Authorization configured here only covers whether a request is
     * authenticated at all (RBAC layer 1 at the URL level). Fine-grained
     * permission/scope/ownership decisions (RBAC layers 2-4) are
     * deliberately left to {@code AccessControlService}/
     * {@code @RequiresOwnership} in the relevant service/controller (see
     * the {@code authorization} package), so role-per-URL rules are not
     * duplicated here as a second source of truth that could drift out
     * of sync with the real permission logic.
     *
     * @param http the security builder to configure
     * @return the filter chain applied to all requests
     * @throws Exception if the underlying {@link HttpSecurity} builder fails to build
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            // Authentication is stateless (access token in the Authorization header, no
            // server session cookie), so there is no session-cookie-based CSRF risk to protect against.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(authenticationEntryPoint) // 401 - not authenticated: missing/invalid token
                    .accessDeniedHandler(accessDeniedHandler)           // 403 - authenticated but lacking the required permission
            )
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints: healthcheck, login/refresh/activation/Google SSO and demo endpoints that don't require login.
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()

                    // Every other business endpoint only needs a valid access token at the URL
                    // level. Concrete permission/scope/ownership decisions (RBAC layers 2-4) are
                    // handled by AccessControlService/@RequiresOwnership in the relevant
                    // service/controller - see the authorization package - so role-per-URL
                    // rules are not repeated here and can't conflict with that source of truth.
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.decoder(jwtDecoder))
            )
            // Attach userId to the MDC right after the token is authenticated, so every
            // log.info(...)/log.warn(...) call made afterwards (in services, exception
            // handlers, etc.) automatically carries it - see UserContextMdcFilter.
            .addFilterAfter(new UserContextMdcFilter(), BearerTokenAuthenticationFilter.class)
            // RBAC layer 1 (Authentication Freshness, ) must run after an
            // Authentication is available (needs to read "sub"/"sid") but before the request
            // reaches the controller/@PreAuthorize
            .addFilterAfter(new AuthenticationFreshnessFilter(userDirectoryService, sessionRegistryService, handlerExceptionResolver),
                    UserContextMdcFilter.class);

        return http.build();
    }
}
