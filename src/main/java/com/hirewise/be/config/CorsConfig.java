package com.hirewise.be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration so the React SPA (Vite dev server, or a deployed
 * static host) can call this API from the browser. The bean is picked up
 * by {@code SecurityConfig#securityFilterChain} via
 * {@code HttpSecurity#cors} - Spring Security's CORS handling runs before
 * the authorization rules and needs this bean to answer the browser's
 * preflight {@code OPTIONS} request, otherwise every request from the
 * frontend is rejected before it even reaches a controller.
 */
@Configuration
public class CorsConfig {

    /**
     * Builds the CORS rule set applied to every {@code /**} path.
     *
     * @param allowedOrigins comma-separated list of origins allowed to call
     *                        this API (see {@code app.cors.allowed-origins}
     *                        / env var {@code CORS_ALLOWED_ORIGINS})
     * @return the configuration source used by the security filter chain
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // The frontend reads the Location header off 201 Created responses
        // (see UserAdminController#create) to know the new resource's URL.
        configuration.setExposedHeaders(List.of("Location"));
        // Auth is a Bearer token in the Authorization header, not a cookie -
        // credentials don't need to flow, so this stays false.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
