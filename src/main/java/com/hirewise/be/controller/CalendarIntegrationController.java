package com.hirewise.be.controller;

import com.hirewise.be.dto.response.AuthorizationUrlResponseDto;
import com.hirewise.be.dto.response.CalendarConnectionStatusResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.CalendarIntegrationService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * UC-18 — Configure and sync Calendar API.
 *
 * <p>Mirrors {@code CloudStorageIntegrationController} in structure.
 * The {@code /connect} endpoint requires a valid access token and
 * returns the authorization URL as JSON (the frontend opens it in a
 * popup). {@code /callback} is {@code permitAll} in
 * {@code SecurityConfig} because the provider's own redirect never
 * carries our Authorization header — the anti-CSRF {@code state}
 * parameter provides equivalent protection (see {@code OAuthStateStore}).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/integrations/calendar")
public class CalendarIntegrationController {

    private final CalendarIntegrationService calendarIntegrationService;
    private final String frontendRedirectUrl;

    public CalendarIntegrationController(
            CalendarIntegrationService calendarIntegrationService,
            @Value("${app.integration.calendar-frontend-redirect-url}")
            String frontendRedirectUrl) {
        this.calendarIntegrationService = calendarIntegrationService;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    /**
     * Current Calendar connection status.
     *
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return current status (never 404 — "never connected" is a normal value)
     */
    @GetMapping
    public ResponseEntity<CalendarConnectionStatusResponseDto> status(
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(calendarIntegrationService.getStatus(currentUser));
    }

    /**
     * Returns the URL to open for the provider's consent screen.
     *
     * @param provider    path segment, e.g. {@code "google-calendar"} or
     *                    {@code "outlook-calendar"}
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the authorization URL to open in a popup
     */
    @GetMapping("/{provider}/connect")
    public ResponseEntity<AuthorizationUrlResponseDto> connect(
            @PathVariable String provider,
            @CurrentUserPrincipal CurrentUser currentUser) {
        String authorizationUrl =
                calendarIntegrationService.buildAuthorizationUrl(provider, currentUser);
        return ResponseEntity.ok(
                AuthorizationUrlResponseDto.builder()
                        .authorizationUrl(authorizationUrl)
                        .build());
    }

    /**
     * Provider's redirect back to us after HR Admin allows or denies consent.
     * PUBLIC endpoint (see class Javadoc) — always redirects to
     * {@code app.integration.calendar-frontend-redirect-url} with the
     * outcome in query params instead of returning a JSON body.
     *
     * @param provider path segment, e.g. {@code "google-calendar"}
     * @param code     present on success
     * @param state    anti-CSRF value (see {@code OAuthStateStore})
     * @param error    present when HR Admin denied consent (EX-01)
     * @return 302 redirect to the frontend
     */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        boolean connected =
                calendarIntegrationService.handleCallback(provider, code, state, error);

        URI redirectTo = UriComponentsBuilder.fromUriString(frontendRedirectUrl)
                .queryParam("provider", provider)
                .queryParam("connected", connected)
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectTo).build();
    }

    /**
     * UC-18 step 4 — Test Connection (BR-INTEG-01): probes the Calendar API
     * with the stored access token.
     *
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the current status (always {@code CONNECTED} on success)
     */
    @PostMapping("/test")
    public ResponseEntity<CalendarConnectionStatusResponseDto> testConnection(
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(calendarIntegrationService.testConnection(currentUser));
    }

    /**
     * UC-18 AF-01 (Disconnect): revokes the current connection.
     *
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the updated (now Revoked) status
     */
    @PostMapping("/disconnect")
    public ResponseEntity<CalendarConnectionStatusResponseDto> disconnect(
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(calendarIntegrationService.disconnect(currentUser));
    }
}
