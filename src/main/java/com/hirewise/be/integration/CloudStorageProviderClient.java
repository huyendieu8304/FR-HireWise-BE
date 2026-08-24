package com.hirewise.be.integration;

import com.hirewise.be.domain.IntegrationProvider;

/**
 * Provider-specific side of UC-07/UC-08: builds the OAuth 2.0 authorization
 * URL, exchanges an authorization code for tokens, and creates the root
 * folder BR-STORAGE-03 requires on first connect. One implementation per
 * {@link IntegrationProvider} ({@link GoogleDriveProviderClient},
 * {@link DropboxProviderClient}); {@code CloudStorageIntegrationService}
 * picks the right one by provider at runtime. Each implementation holds its
 * own {@code redirect_uri} (from configuration) and reuses it for both
 * methods below, since the two must be byte-identical per the OAuth 2.0
 * spec - there is no reason for callers to pass it in separately.
 */
public interface CloudStorageProviderClient {

    /** @return which provider this client talks to */
    IntegrationProvider provider();

    /**
     * Builds the URL the HR Admin's browser is sent to for the provider's
     * consent screen (UC-07 step 2-3 / UC-08 Reconnect step 4).
     *
     * @param state opaque, single-use anti-CSRF value the provider must echo
     *              back unchanged on the callback (see {@code OAuthStateStore})
     * @return the full authorization URL
     */
    String buildAuthorizationUrl(String state);

    /**
     * Exchanges the authorization code the provider's redirect carried for
     * an access/refresh token pair (UC-07 step 5).
     *
     * @param code the {@code code} query parameter from the callback
     * @return the obtained tokens
     * @throws IntegrationConnectException if the provider rejects the code or the call fails
     */
    OAuthTokenResponse exchangeAuthorizationCode(String code);

    /**
     * Best-effort creation of the root folder (BR-STORAGE-03) HireWise
     * stores files under. Failure here does NOT fail the overall connect -
     * the OAuth connection itself already succeeded, and the folder can be
     * created lazily later; this is why the method returns {@code null}
     * instead of throwing on error (UC-07 "Other Information": handle
     * provider API unavailability gracefully).
     *
     * @param accessToken a just-obtained, still-valid access token
     * @return the provider-side id of the created (or found) root folder, or
     *         {@code null} if it could not be created right now
     */
    String createRootFolder(String accessToken);
}
