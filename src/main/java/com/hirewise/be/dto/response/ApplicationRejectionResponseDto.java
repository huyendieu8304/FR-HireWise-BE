package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * UC-20/UC-29: the rejection record for an Application, when it has one
 * ({@code application_rejections}, BR-REJ-01/03) - {@code null} on
 * {@code ApplicationDetailResponseDto} for an Application that was never
 * rejected.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationRejectionResponseDto {
    private Long reasonId;
    private String reasonCode;
    private String reasonLabel;
    private String customMessage;
    private String rejectedByName;
    private Instant rejectedAt;
}
