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

import java.util.Map;

/**
 * Dropbox side of UC-07/UC-08 (OAuth 2.0 authorization code flow, plus
 * root-folder creation for BR-STORAGE-03).
 */
@Slf4j
@Component
public class DropboxProviderClient implements CloudStorageProviderClient {

    private static final String AUTHORIZATION_ENDPOINT = "https://www.dropbox.com/oauth2/authorize";
    private static final String TOKEN_ENDPOINT = "https://api.dropboxapi.com/oauth2/token";
    private static final String API_BASE_URL = "https://api.dropboxapi.com/2";
    private static final String ROOT_FOLDER_PATH = "/HireWise";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient tokenClient = RestClient.create();
    private final RestClient apiClient = RestClient.builder().baseUrl(API_BASE_URL).build();

    public DropboxProviderClient(
            @Value("${app.integration.dropbox.client-id:}") String clientId,
            @Value("${app.integration.dropbox.client-secret:}") String clientSecret,
            @Value("${app.integration.dropbox.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.DROPBOX;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                // token_access_type=offline: equivalent of Google's access_type=offline -
                // without it Dropbox does not hand back a refresh_token at all.
                .queryParam("token_access_type", "offline")
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

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Dropbox token exchange failed", e);
        }
    }

    @Override
    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        requireConfigured();
        // Dropbox's refresh_token grant: same token endpoint as the authorization_code
        // exchange above, no redirect_uri involved this time. Dropbox does not
        // reissue a new refresh_token here - callers must keep reusing the one
        // passed in.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        try {
            return tokenClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);
        } catch (RestClientException e) {
            // Most commonly a revoked/expired refresh_token (e.g. HR Admin revoked
            // HireWise's access from their Dropbox account settings) - the caller
            // (CloudStorageTokenRefreshWorker) falls back to marking the connection
            // EXPIRED so UC-08's normal Reconnect flow can recover it.
            throw new IntegrationConnectException("Dropbox token refresh failed", e);
        }
    }

    @Override
    public String createRootFolder(String accessToken) {
        try {
            Map<String, Object> body = Map.of("path", ROOT_FOLDER_PATH);
            Map<?, ?> response = apiClient.post()
                    .uri("/files/create_folder_v2")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return null;
            }
            Object metadata = response.get("metadata");
            return metadata instanceof Map<?, ?> metadataMap ? (String) metadataMap.get("id") : null;
        } catch (RestClientException e) {
            // Non-fatal by design (also covers the common case where the folder
            // already exists from a previous connect - Dropbox returns a 409 for
            // that, which is fine to just log and move on from).
            log.warn("Failed to create the Dropbox root folder right after connecting: {}", e.getMessage());
            return null;
        }
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IntegrationConnectException(
                    "Dropbox integration is not configured (app.integration.dropbox.client-id/client-secret)");
        }
    }
}
