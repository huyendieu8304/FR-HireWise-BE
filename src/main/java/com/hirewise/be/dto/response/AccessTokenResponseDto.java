package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response of {@code POST /api/auth/refresh}: a freshly-minted access token. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessTokenResponseDto {
    private String accessToken;
    private long expiresIn;
    private String tokenType;
}
