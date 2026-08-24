package com.hirewise.be.controller;

import com.hirewise.be.dto.response.AuthorizationUrlResponseDto;
import com.hirewise.be.dto.response.StorageConnectionStatusResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.CloudStorageIntegrationService;
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
 * UC-07 (Connect Cloud Storage via OAuth 2.0) and UC-08 (check status /
 * reconnect / disconnect).
 * <p>
 * {@code /connect} requires a valid access token and returns the
 * authorization URL as JSON rather than redirecting - a plain browser
 * navigation to a protected endpoint would not carry our Authorization
 * header (this API is stateless-JWT, no session cookie), so the frontend
 * calls this endpoint normally (authenticated fetch) and then itself opens
 * the returned URL, per the Screen Description, in a popup. {@code
 * /callback} is the one exception to "every endpoint requires auth": it is
 * hit by the PROVIDER's redirect (Google/Dropbox), which never carries our
 * access token either, so it is deliberately {@code permitAll} in
 * {@code SecurityConfig} and relies entirely on the OAuth {@code state}
 * parameter instead (see {@code integration.OAuthStateStore}).
 */
@Slf4j
@RestController
@RequestMapping("/api/integrations/cloud-storage")
public class CloudStorageIntegrationController {

    private final CloudStorageIntegrationService cloudStorageIntegrationService;
    private final String frontendRedirectUrl;

    public CloudStorageIntegrationController(
            CloudStorageIntegrationService cloudStorageIntegrationService,
            @Value("${app.integration.frontend-redirect-url}") String frontendRedirectUrl) {
        this.cloudStorageIntegrationService = cloudStorageIntegrationService;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    /**
     * UC-08 step 2: current Cloud Storage connection status.
     *
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the current status (never 404 - "never connected" is a normal status value)
     */
    @GetMapping
    public ResponseEntity<StorageConnectionStatusResponseDto> status(@CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(cloudStorageIntegrationService.getStatus(currentUser));
    }

    /**
     * UC-07 step 1-3 / UC-08 AF-01 (Reconnect) step 3-4: returns the URL to
     * open for the provider's consent screen.
     *
     * @param provider    path segment, e.g. {@code "google-drive"} or {@code "dropbox"}
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the authorization URL to open
     */
    @GetMapping("/{provider}/connect")
    public ResponseEntity<AuthorizationUrlResponseDto> connect(
            @PathVariable String provider, @CurrentUserPrincipal CurrentUser currentUser) {
        String authorizationUrl = cloudStorageIntegrationService.buildAuthorizationUrl(provider, currentUser);
        return ResponseEntity.ok(AuthorizationUrlResponseDto.builder().authorizationUrl(authorizationUrl).build());
    }

    /**
     * the provider's redirect back to
     * us after the HR Admin allows or denies consent. PUBLIC endpoint (see
     * class Javadoc) - always redirects the browser on to
     * {@code app.integration.frontend-redirect-url}, tagged with whether it
     * succeeded, rather than returning a JSON body or an error status that
     * a plain browser redirect target could not do anything useful with.
     *
     * @param provider path segment, e.g. {@code "google-drive"} or {@code "dropbox"}
     * @param code     present on success
     * @param state    anti-CSRF value that must match a pending connect (see
     *                 {@code integration.OAuthStateStore})
     * @param error    present when the HR Admin denied consent (EX-01)
     * @return a 302 redirect to the frontend
     */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        boolean connected = cloudStorageIntegrationService.handleCallback(provider, code, state, error);

        URI redirectTo = UriComponentsBuilder.fromUriString(frontendRedirectUrl)
                .queryParam("provider", provider)
                .queryParam("connected", connected)
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectTo).build();
    }

    /**
     * UC-08 AF-01 (Disconnect).
     *
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the updated (now Revoked) status
     */
    @PostMapping("/disconnect")
    public ResponseEntity<StorageConnectionStatusResponseDto> disconnect(@CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(cloudStorageIntegrationService.disconnect(currentUser));
    }
}
