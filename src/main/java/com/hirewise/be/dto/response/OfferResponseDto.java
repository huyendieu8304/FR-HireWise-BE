package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-36/UC-37: an Offer as the Recruiter sees it, including
 * {@code renderedBody} for the "Preview noi dung Offer" control of the
 * Offer Review & Send screen.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferResponseDto {
    private UUID id;
    private UUID applicationId;
    private String candidateName;
    private String jobTitle;
    private Long offerTemplateId;
    private String offerTemplateName;
    private BigDecimal salary;
    private BigDecimal probationRate;
    private LocalDate startDate;
    private Instant expiresAt;
    private String status;
    private String renderedBody;
    private Instant sentAt;
    private Instant signedAt;
    private Instant createdAt;
}
