package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * UC-38 steps 4-5: the full offer terms, returned only after the OTP has
 * been verified (BR-OFFER-03).
 * <p>
 * {@code renderedBody} is the snapshot frozen at creation time (UC-36 step
 * 5), not a fresh render, so the candidate reads exactly the wording the
 * Recruiter approved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicOfferContentDto {
    private String jobTitle;
    private String companyName;
    private String candidateName;
    private BigDecimal salary;
    private BigDecimal probationRate;
    private LocalDate startDate;
    private Instant expiresAt;
    private String status;
    private String renderedBody;
    /** {@code true} once signed - the page then shows the receipt instead of the signature pad. */
    private boolean signed;
    private Instant signedAt;
}
