package com.hirewise.be.domain;

/**
 * Health of an {@link IntegrationConnection} (LV-32). {@link StorageConnection}
 * deliberately has no status column of its own - callers read this value via
 * {@code StorageConnection.integrationConnection.status} so the two can never
 * drift out of sync.
 * <ul>
 *   <li>{@code CONNECTED} - token valid, uploads allowed.</li>
 *   <li>{@code EXPIRED} - token needs a Reconnect (UC-08); BR-STORAGE-02 pauses
 *       new uploads until it is.</li>
 *   <li>{@code REVOKED} - manually disconnected (UC-08 AF-01).</li>
 * </ul>
 */
public enum ConnectionStatus {
    CONNECTED,
    EXPIRED,
    REVOKED
}
