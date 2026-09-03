package com.hirewise.be.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * UC-36 main flow: request body for
 * {@code POST /api/applications/{applicationId}/offers}, one field per row
 * of the "Offer Creation Form" screen description.
 * <p>
 * {@code probationRate} is the only optional field - left {@code null} the
 * service applies the 85% default named in the screen description.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOfferRequestDto {

    @NotNull(message = "{validation.offer.offer_template_id.required}")
    private Long offerTemplateId;

    @NotNull(message = "{validation.offer.salary.required}")
    @Positive(message = "{validation.offer.salary.positive}")
    private BigDecimal salary;

    @DecimalMin(value = "0.01", message = "{validation.offer.probation_rate.range}")
    @DecimalMax(value = "100.00", message = "{validation.offer.probation_rate.range}")
    private BigDecimal probationRate;

    @NotNull(message = "{validation.offer.start_date.required}")
    @Future(message = "{validation.offer.start_date.future}")
    private LocalDate startDate;

    @NotNull(message = "{validation.offer.expires_at.required}")
    @Future(message = "{validation.offer.expires_at.future}")
    private Instant expiresAt;
}
