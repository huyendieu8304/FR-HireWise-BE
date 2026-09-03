package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-38 step 3: the 6-digit code typed into the "O nhap OTP" control
 * (BR-OFFER-03).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOfferOtpRequestDto {

    @NotBlank(message = "{validation.offer_otp.code.required}")
    // Exactly six digits - the shape OfferAccessService generates.
    @Pattern(regexp = "^\\d{6}$", message = "{validation.offer_otp.code.pattern}")
    private String code;
}
