package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * UC-38 step 1: what the candidate may see BEFORE passing OTP - just enough
 * to recognise the offer is theirs and know the deadline.
 * <p>
 * BR-OFFER-03 requires the contract terms to stay hidden until the code is
 * verified, so this DTO deliberately carries no salary, start date or body.
 * The email address is masked: the page is reachable by anyone holding the
 * link, and echoing the full address back would leak it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicOfferSummaryDto {
    private String jobTitle;
    private String companyName;
    private String candidateName;
    /** Masked, e.g. {@code n***a@example.com} - only enough to confirm where the code went. */
    private String maskedEmail;
    private Instant expiresAt;
    private String status;
    private boolean otpVerified;
}
