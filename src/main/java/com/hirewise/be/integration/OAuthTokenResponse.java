package com.hirewise.be.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common shape of a Google/Dropbox OAuth 2.0 token endpoint response (both
 * follow the standard OAuth 2.0 JSON token response fields). Unknown fields
 * (e.g. Google's {@code scope}, Dropbox's {@code account_id}/{@code uid})
 * are ignored - only what UC-07/UC-08 actually need is kept.
 *
 * @param accessToken      short-lived token used to call the provider's file API
 * @param refreshToken     long-lived token used to obtain a new access token later;
 *                         {@code null} on some responses (e.g. a re-consent that
 *                         Google/Dropbox decided not to reissue one for)
 * @param tokenType        normally {@code "Bearer"}
 * @param expiresInSeconds access token lifetime in seconds from time of issue
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresInSeconds
) {
}
