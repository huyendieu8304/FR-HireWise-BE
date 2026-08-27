package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * UC-08 status screen: what Cloud Storage provider (if any) is connected
 * and its current health. {@link #status} is {@code null} when no
 * connection has ever been created - the frontend renders that the same
 * way as {@code EXPIRED} (red badge, per UC-07 Screen Description REF 3:
 * "Do = Token Expired/chua ket noi").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageConnectionStatusResponseDto {
    private boolean connected;
    private IntegrationProvider provider;
    private ConnectionStatus status;
    private String accountLabel;
    private String rootFolderId;
    private Instant connectedAt;
    private Instant tokenExpiresAt;

    /** @return the "nothing connected yet" status, e.g. before UC-07 has ever run */
    public static StorageConnectionStatusResponseDto notConnected() {
        return StorageConnectionStatusResponseDto.builder().connected(false).build();
    }
}
