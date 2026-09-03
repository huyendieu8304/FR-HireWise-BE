package com.hirewise.be.integration;

import com.hirewise.be.domain.IntegrationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Outlook Calendar side of UC-18 via the Microsoft Graph API
 * ({@code https://graph.microsoft.com/v1.0}).
 *
 * <p>Uses the common tenant endpoint so any organizational or personal
 * Microsoft account can authorize the app — the exact tenant is resolved
 * at token-exchange time. Scope {@code Calendars.Read} is the minimum
 * needed for Test Connection; add {@code Calendars.ReadWrite} when
 * UC-24/25/26 event creation is implemented.</p>
 */
@Slf4j
@Component
public class OutlookCalendarProviderClient implements CalendarProviderClient {

    private static final String AUTHORIZATION_ENDPOINT =
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String TOKEN_ENDPOINT =
            "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String GRAPH_API_BASE_URL =
            "https://graph.microsoft.com/v1.0";
    /**
     * Readonly scope for UC-18 Test Connection, plus {@code offline_access}
     * for refresh tokens. Extend to {@code Calendars.ReadWrite} for UC-24/25/26.
     */
    private static final String SCOPE =
            "Calendars.Read offline_access";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient tokenClient = RestClient.create();
    private final RestClient graphClient =
            RestClient.builder().baseUrl(GRAPH_API_BASE_URL).build();

    public OutlookCalendarProviderClient(
            @Value("${app.integration.outlook-calendar.client-id:}") String clientId,
            @Value("${app.integration.outlook-calendar.client-secret:}") String clientSecret,
            @Value("${app.integration.outlook-calendar.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.OUTLOOK_CALENDAR;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                // prompt=select_account: always ask the HR Admin to pick/confirm the
                // account, avoiding silent reuse of a cached wrong account in shared
                // browser profiles.
                .queryParam("prompt", "select_account")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeAuthorizationCode(String code) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        form.add("scope", SCOPE);

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException(
                    "Outlook Calendar token exchange failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Microsoft's {@code refresh_token} grant uses the same token endpoint
     * as the authorization_code exchange. Unlike Google, Microsoft may reissue
     * a new refresh token on some calls; callers should update the stored value
     * when a new one is present in the response.</p>
     */
    @Override
    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");
        form.add("scope", SCOPE);

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException(
                    "Outlook Calendar token refresh failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /me} on Microsoft Graph — the lightest possible
     * authenticated endpoint, verifying the token is valid without
     * any calendar-specific permission requirement beyond the login scope.</p>
     */
    @Override
    public void testConnection(String accessToken) {
        try {
            log.info("Outlook Calendar test connection starting");
            graphClient.get()
                    .uri("/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Outlook Calendar test connection succeeded");
        } catch (RestClientException e) {
            throw new IntegrationConnectException(
                    "Outlook Calendar API probe failed: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new IntegrationConnectException(
                    "Outlook Calendar integration is not configured"
                    + " (app.integration.outlook-calendar.client-id/client-secret)");
        }
    }
}
