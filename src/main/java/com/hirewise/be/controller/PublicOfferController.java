package com.hirewise.be.controller;

import com.hirewise.be.dto.request.VerifyOfferOtpRequestDto;
import com.hirewise.be.dto.response.PublicOfferContentDto;
import com.hirewise.be.dto.response.PublicOfferSummaryDto;
import com.hirewise.be.service.OfferAccessService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M18 - Offer & e-Signature, candidate-facing half: UC-38 (open the secure
 * link and verify the OTP before reading the contract).
 * <p>
 * <strong>These endpoints are deliberately outside RBAC.</strong> Candidates
 * have no account in this system (SRS section 3.1), so there is no
 * {@code CurrentUser} to authorize and {@code PermissionCodes.OFFER_SIGN} -
 * granted to the CANDIDATE role back in V2 - is unusable here. They are
 * declared {@code permitAll} in {@code SecurityConfig}, exactly like the
 * public job board and apply endpoints, and every call is authenticated by
 * the link token plus the verified one-time code inside
 * {@link OfferAccessService}. This is not a missing permission check.
 */
@RestController
@RequestMapping("/api/public/offers")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublicOfferController {

    OfferAccessService offerAccessService;

    /**
     * UC-38 step 1: minimal detail shown before the OTP is verified - never
     * the contract terms (BR-OFFER-03).
     *
     * @param token the raw link token from the emailed URL
     * @return job title, deadline, masked email and whether an OTP is already verified
     */
    @GetMapping("/{token}")
    public ResponseEntity<PublicOfferSummaryDto> getSummary(@PathVariable String token) {
        return ResponseEntity.ok(offerAccessService.getSummary(token));
    }

    /**
     * UC-38 step 2: sends a one-time code to the candidate's email. Also the
     * "Gui lai ma" control, which is throttled per token.
     *
     * @param token the raw link token from the emailed URL
     */
    @PostMapping("/{token}/otp")
    public ResponseEntity<Void> requestOtp(@PathVariable String token) {
        offerAccessService.requestOtp(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * UC-38 steps 3-5: verifies the code and returns the full contract terms.
     *
     * @param token   the raw link token from the emailed URL
     * @param request the 6-digit code the candidate typed
     * @return the offer's full terms
     */
    @PostMapping("/{token}/otp/verify")
    public ResponseEntity<PublicOfferContentDto> verifyOtp(
            @PathVariable String token,
            @Valid @RequestBody VerifyOfferOtpRequestDto request) {
        return ResponseEntity.ok(offerAccessService.verifyOtp(token, request));
    }

    /**
     * UC-38 step 5 on reload: re-serves the terms while the last verification
     * is still inside the viewing window.
     *
     * @param token the raw link token from the emailed URL
     * @return the offer's full terms
     */
    @GetMapping("/{token}/content")
    public ResponseEntity<PublicOfferContentDto> getContent(@PathVariable String token) {
        return ResponseEntity.ok(offerAccessService.getContent(token));
    }
}
