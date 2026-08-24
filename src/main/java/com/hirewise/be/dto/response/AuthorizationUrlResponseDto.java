package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-07 step 2-3 / UC-08 Reconnect: the URL the frontend opens (per the
 * Screen Description, in a popup) to send the HR Admin to the provider's
 * OAuth 2.0 consent screen. Returned as JSON rather than an HTTP redirect
 * because this endpoint requires a valid access token (Authorization
 * header), which a plain browser navigation would not carry - see
 * {@code controller.CloudStorageIntegrationController}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizationUrlResponseDto {
    private String authorizationUrl;
}
