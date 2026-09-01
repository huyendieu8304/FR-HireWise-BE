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
    // Dropbox splits its API across two hosts: api.dropboxapi.com for RPC-style calls
    // (used above) and content.dropboxapi.com for anything that streams a file body.
    private static final String CONTENT_BASE_URL = "https://content.dropboxapi.com/2";
    private static final String ROOT_FOLDER_PATH = "/HireWise";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient tokenClient = RestClient.create();
    private final RestClient apiClient = RestClient.builder().baseUrl(API_BASE_URL).build();
    private final RestClient contentClient = RestClient.builder().baseUrl(CONTENT_BASE_URL).build();

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
            apiClient.post()
                    .uri("/files/create_folder_v2")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            // Unlike Google Drive's opaque folder id, Dropbox addresses everything by
            // PATH (see #uploadFile) - the stable, well-known ROOT_FOLDER_PATH itself is
            // what callers need here, not the create call's own (equally opaque) metadata id.
            return ROOT_FOLDER_PATH;
        } catch (RestClientException e) {
            // Non-fatal by design (also covers the common case where the folder already
            // exists from a previous connect - Dropbox returns a 409 for that, which just
            // means the root folder is already there, so it's still safe to return its path).
            log.warn("Create Dropbox root folder call did not succeed cleanly (may already exist): {}", e.getMessage());
            return ROOT_FOLDER_PATH;
        }
    }

    @Override
    public String uploadFile(String accessToken, String rootFolderId, String subfolderName, String fileName, String mimeType, byte[] content) {
        try {
            // Dropbox addresses files/folders by path, not by an opaque parent id -
            // rootFolderId here IS the BR-STORAGE-03 root folder path (see createRootFolder).
            String parentPath = rootFolderId != null ? rootFolderId : ROOT_FOLDER_PATH;
            String subfolderPath = subfolderName != null && !subfolderName.isBlank() ? "/" + sanitizeForPath(subfolderName) : "";
            String targetPath = parentPath + subfolderPath + "/" + sanitizeForPath(fileName);
            // Dropbox-API-Arg carries call arguments as a JSON string in a header
            // (the body itself is the raw file content) - built by hand since it's
            // a single small, fully-controlled object.
            String apiArg = "{\"path\":\"" + escapeJson(targetPath) + "\",\"mode\":\"add\",\"autorename\":true,\"mute\":false}";
            MediaType contentType = mimeType != null && !mimeType.isBlank()
                    ? MediaType.parseMediaType(mimeType) : MediaType.APPLICATION_OCTET_STREAM;

            Map<?, ?> response = contentClient.post()
                    .uri("/files/upload")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Dropbox-API-Arg", apiArg);
                        headers.setContentType(contentType);
                    })
                    .body(content)
                    .retrieve()
                    .body(Map.class);
            return response == null ? null : (String) response.get("id");
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Dropbox file upload failed", e);
        }
    }

    private static String sanitizeForPath(String fileName) {
        return fileName == null || fileName.isBlank() ? "file" : fileName.replace("/", "_");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String getViewUrl(String accessToken, String externalFileId) {
        // Dropbox files/get_temporary_link returns a direct HTTPS link valid for
        // approximately 4 hours. The path argument accepts both "/folder/file.pdf"
        // paths and "id:..." file ids - since externalFileId is the Dropbox file id
        // returned by uploadFile (response["id"]), we pass it directly.
        try {
            Map<String, Object> body = Map.of("path", externalFileId);
            Map<?, ?> response = apiClient.post()
                    .uri("/files/get_temporary_link")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !response.containsKey("link")) {
                throw new IntegrationConnectException(
                        "Dropbox did not return a temporary link for file " + externalFileId);
            }
            return (String) response.get("link");
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Failed to get Dropbox temporary link for file " + externalFileId, e);
        }
    }

    @Override
    public byte[] downloadFile(String accessToken, String externalFileId) {
        try {
            String apiArg = "{\"path\":\"" + escapeJson(externalFileId) + "\"}";
            return contentClient.post()
                    .uri("/files/download")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Dropbox-API-Arg", apiArg);
                    })
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new IntegrationConnectException("Failed to download file from Dropbox: " + externalFileId, e);
        }
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IntegrationConnectException(
                    "Dropbox integration is not configured (app.integration.dropbox.client-id/client-secret)");
        }
    }
}
