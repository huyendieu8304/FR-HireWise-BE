package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** exchanges a still-valid refresh token for a new access token. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequestDto {

    @NotBlank(message = "{validation.auth.refresh_token.required}")
    private String refreshToken;
}
