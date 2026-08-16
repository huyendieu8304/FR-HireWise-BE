package com.hirewise.be.config;

import com.hirewise.be.security.CustomAccessDeniedHandler;
import com.hirewise.be.security.CustomAuthenticationEntryPoint;
import com.hirewise.be.security.KeycloakRoleConverter;
import com.hirewise.be.security.UserContextMdcFilter;
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

/**
 * Spring Security cấu hình theo mô hình OAuth2 Resource Server: app KHÔNG
 * tự quản lý user/mật khẩu, chỉ xác thực chữ ký + hạn dùng của access
 * token do Keycloak phát hành (JWT), rồi map role trong token sang
 * GrantedAuthority để phục vụ RBAC.
 *
 * Việc lấy public key để verify JWT được Spring Boot tự động thực hiện
 * (qua JWK Set endpoint) dựa trên
 * spring.security.oauth2.resourceserver.jwt.issuer-uri khai báo trong
 * application.properties - không cần cấu hình thủ công JwtDecoder.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // cho phép dùng @PreAuthorize("hasRole('ADMIN')") ở tầng service/controller
public class SecurityConfig {

    private final KeycloakRoleConverter keycloakRoleConverter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(KeycloakRoleConverter keycloakRoleConverter,
                           CustomAuthenticationEntryPoint authenticationEntryPoint,
                           CustomAccessDeniedHandler accessDeniedHandler) {
        this.keycloakRoleConverter = keycloakRoleConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRoleConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(authenticationEntryPoint) // 401 - un-authentication thiếu/token sai
                    .accessDeniedHandler(accessDeniedHandler)           // 403 - un-authorization đủ xác thực nhưng thiếu quyền
            )
            .authorizeHttpRequests(auth -> auth
                    // Public: healthcheck, endpoint demo không cần đăng nhập
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()

                    // RBAC theo role Keycloak (đã map "ROLE_xxx" trong KeycloakRoleConverter)
//                    .requestMatchers(HttpMethod.POST, "/api/job-postings").hasAnyRole("ADMIN", "RECRUITER")
//                    .requestMatchers(HttpMethod.PATCH, "/api/job-postings/*/close").hasAnyRole("ADMIN", "RECRUITER")
//                    .requestMatchers(HttpMethod.DELETE, "/api/job-postings/**").hasRole("ADMIN")
//                    .requestMatchers(HttpMethod.GET, "/api/job-postings/**").authenticated()

                    // Còn lại: chỉ cần đăng nhập hợp lệ (role cụ thể tự kiểm ở @PreAuthorize nếu cần)
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            // Gắn userId/role vào MDC ngay sau khi JWT được xác thực xong, để mọi
            // log log.info(...)/log.warn(...) phát sinh sau đó (service, exception
            // handler...) tự động có sẵn 2 field này - xem UserContextMdcFilter.
            .addFilterAfter(new UserContextMdcFilter(), BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
