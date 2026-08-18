package com.hirewise.be.config;

import com.hirewise.be.security.AuthenticationFreshnessFilter;
import com.hirewise.be.security.CustomAccessDeniedHandler;
import com.hirewise.be.security.CustomAuthenticationEntryPoint;
import com.hirewise.be.security.KeycloakRoleConverter;
import com.hirewise.be.security.UserContextMdcFilter;
import com.hirewise.be.security.UserDirectoryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Configures Spring Security as an OAuth2 resource server: the app does
 * NOT manage users/passwords itself, it only verifies the signature and
 * expiry of the access token (JWT) issued by Keycloak, then maps the
 * roles carried in the token to {@code GrantedAuthority} for RBAC.
 * <p>
 * The public key used to verify the JWT is resolved automatically by
 * Spring Boot (via the JWK Set endpoint) based on
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} declared
 * in application.properties - no manual {@code JwtDecoder} configuration
 * is needed here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables method-level checks such as @PreAuthorize("hasRole('ADMIN')") in the service/controller layers
public class SecurityConfig {

    private final KeycloakRoleConverter keycloakRoleConverter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final UserDirectoryService userDirectoryService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(KeycloakRoleConverter keycloakRoleConverter,
                           CustomAuthenticationEntryPoint authenticationEntryPoint,
                           CustomAccessDeniedHandler accessDeniedHandler,
                           UserDirectoryService userDirectoryService,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.keycloakRoleConverter = keycloakRoleConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.userDirectoryService = userDirectoryService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    /**
     * @return the converter that turns realm/client roles carried by the
     *         Keycloak-issued JWT into Spring Security
     *         {@code GrantedAuthority} instances used by RBAC checks
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRoleConverter);
        return converter;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Authentication is stateless JWT-based (no server session, see below),
            // so there is no session-cookie-based CSRF risk to protect against.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(authenticationEntryPoint) // 401 - not authenticated: missing/invalid token
                    .accessDeniedHandler(accessDeniedHandler)           // 403 - authenticated but lacking the required permission
            )
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints: healthcheck and demo endpoints that don't require login
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()

                    // Every other business endpoint only needs a valid JWT at the URL level.
                    // Concrete permission/scope/ownership decisions (RBAC layers 2-4) are
                    // handled by AccessControlService/@RequiresOwnership in the relevant
                    // service/controller - see the authorization package - so role-per-URL
                    // rules are not repeated here and can't conflict with that source of truth.
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            // Attach userId/role to the MDC right after the JWT is authenticated, so every
            // log.info(...)/log.warn(...) call made afterwards (in services, exception
            // handlers, etc.) automatically carries these two fields - see UserContextMdcFilter.
            .addFilterAfter(new UserContextMdcFilter(), BearerTokenAuthenticationFilter.class)
            // RBAC layer 1 (Authentication Freshness, BR-AUTH-07) must run after an
            // Authentication is available (needs to read "sub") but before the request
            // reaches the controller/@PreAuthorize - see AuthenticationFreshnessFilter.
            .addFilterAfter(new AuthenticationFreshnessFilter(userDirectoryService, handlerExceptionResolver),
                    UserContextMdcFilter.class);

        return http.build();
    }
}
