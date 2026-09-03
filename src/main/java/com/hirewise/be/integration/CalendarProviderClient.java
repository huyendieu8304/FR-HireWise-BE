package com.hirewise.be.integration;

import com.hirewise.be.domain.IntegrationProvider;

/**
 * Provider-specific OAuth 2.0 operations needed for Calendar integration
 * (UC-18). Each implementation handles one provider ({@link GoogleCalendarProviderClient}
 * for Google Calendar, {@link OutlookCalendarProviderClient} for Outlook via
 * Microsoft Graph). {@code CalendarIntegrationService} selects the right
 * implementation at runtime via the provider enum value.
 *
 * <p>The interface is intentionally narrower than {@link CloudStorageProviderClient}:
 * Calendar integration only needs the OAuth round-trip plus an API health
 * probe for "Test Connection" (UC-18 step 4), not file upload/download.</p>
 */
public interface CalendarProviderClient {

    /** @return which provider this client talks to */
    IntegrationProvider provider();

    /**
     * Builds the URL the HR Admin's browser is sent to for the provider's
     * consent screen (UC-18 step 2-3).
     *
     * @param state opaque, single-use anti-CSRF value the provider must echo
     *              back unchanged on the callback (see {@code OAuthStateStore})
     * @return the full authorization URL
     */
    String buildAuthorizationUrl(String state);

    /**
     * Exchanges the authorization code from the provider's redirect for an
     * access/refresh token pair (UC-18 step 5-6).
     *
     * @param code the {@code code} query parameter from the callback
     * @return the obtained tokens
     * @throws IntegrationConnectException if the provider rejects the code
     *                                      or the HTTP call fails
     */
    OAuthTokenResponse exchangeAuthorizationCode(String code);

    /**
     * Exchanges a stored refresh token for a fresh access token (UC-18
     * background refresh). Providers typically do not reissue a new refresh
     * token on every call — callers must keep using the same one unless the
     * response explicitly contains a new one.
     *
     * @param refreshToken a previously stored, still-valid refresh token
     * @return the refreshed tokens ({@link OAuthTokenResponse#refreshToken()}
     *         is {@code null} when the provider did not reissue one)
     * @throws IntegrationConnectException if the provider rejects the token
     */
    OAuthTokenResponse refreshAccessToken(String refreshToken);

    /**
     * Probes the Calendar API with the given access token to confirm the
     * connection is live (UC-18 step 4 — Test Connection, BR-INTEG-01).
     * Implementations should make the cheapest possible read-only call
     * (e.g., list calendars with maxResults=1).
     *
     * @param accessToken a still-valid access token for the connection
     * @throws IntegrationConnectException if the provider rejects the token
     *                                      or the API is unreachable
     */
    void testConnection(String accessToken);
}
