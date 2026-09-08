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
 * {@code probationRate} is optional - left {@code null} the service applies
 * the 85% default named in the screen description. {@code targetStageId} is
 * optional too, and only the Kanban drag path (UC-23) sends it.
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

    /**
     * UC-23 + UC-36: id of the OFFER-typed Stage the card was dragged onto in
     * the Kanban board. Left {@code null} - the [Tao Offer] button inside the
     * Applicant Card - the Application must already sit at an Offer stage.
     * Set, the service moves the stage inside the very same transaction that
     * creates the Offer, so cancelling the form on the client records nothing.
     * Deliberately unvalidated here: whether the id exists, belongs to this
     * Job's pipeline and is OFFER-typed is a business rule, not a shape rule
     * (same split as {@code ScheduleInterviewRequestDto#targetStageId}).
     */
    private Long targetStageId;
}
