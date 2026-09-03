package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for the Calendar integration connection status
 * (UC-18). Mirrors {@link StorageConnectionStatusResponseDto} in shape
 * but is scoped to the {@code purpose=CALENDAR} connection.
 *
 * <p>When Calendar has never been connected {@code connected} is
 * {@code false} and all other fields are {@code null} — 404 is never
 * returned; "never connected" is a valid normal state.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarConnectionStatusResponseDto {

    private boolean connected;

    /** The provider in use, or {@code null} when not connected. */
    private IntegrationProvider provider;

    /** Current status of the connection, or {@code null} when not connected. */
    private ConnectionStatus status;

    /** When the connection was last established (OAuth completed), or {@code null}. */
    private Instant connectedAt;

    /** When the stored access token expires, or {@code null} if unknown. */
    private Instant tokenExpiresAt;

    /**
     * Convenience factory for the "never connected" response. Returned by
     * {@code CalendarIntegrationService#getStatus} when no
     * {@code integration_connections} row with {@code purpose=CALENDAR}
     * has ever been created.
     *
     * @return a DTO with {@code connected=false} and all other fields null
     */
    public static CalendarConnectionStatusResponseDto notConnected() {
        return CalendarConnectionStatusResponseDto.builder()
                .connected(false)
                .build();
    }
}
