package com.hirewise.be.controller;

import com.hirewise.be.dto.request.ActivateAccountRequestDto;
import com.hirewise.be.dto.request.LoginRequestDto;
import com.hirewise.be.dto.request.RefreshTokenRequestDto;
import com.hirewise.be.dto.response.AccessTokenResponseDto;
import com.hirewise.be.dto.response.LoginResponseDto;
import com.hirewise.be.exception.TooManyRequestsException;
import com.hirewise.be.security.LoginRateLimiter;
import com.hirewise.be.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 *  Login/Logout + activation link. Every endpoint here is
 * {@code permitAll} in {@code SecurityConfig} - authentication is exactly
 * what these endpoints exist to establish.
 */
@RestController
@RequestMapping("/api/auth")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class AuthController {

    AuthService authService;
    LoginRateLimiter loginRateLimiter;

    /** Email/Password login. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        if (!loginRateLimiter.tryAcquire(ip)) {
            throw new TooManyRequestsException();
        }
        LoginResponseDto response = authService.login(request, ip, httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    /** Google SSO login - frontend supplies the ID token obtained from Google Identity Services. */
    @PostMapping("/google")
    public ResponseEntity<LoginResponseDto> loginWithGoogle(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        if (!loginRateLimiter.tryAcquire(ip)) {
            throw new TooManyRequestsException();
        }
        LoginResponseDto response = authService.loginWithGoogle(body.get("idToken"), ip, httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    /** revokes the caller's current session. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) {
            String sid = jwt.getClaimAsString("sid");
            authService.logout(sid != null ? java.util.UUID.fromString(sid) : null);
        }
        return ResponseEntity.noContent().build();
    }

    /** exchanges a refresh token for a new access token. */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** sets the password for a newly-created (Invited) account. */
    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateAccountRequestDto request) {
        authService.activate(request);
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
