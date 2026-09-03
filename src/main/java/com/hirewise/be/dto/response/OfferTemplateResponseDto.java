package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-36 step 2: one selectable row of the "Dropdown Offer Template"
 * control. {@code bodyTemplate} is included so the form can preview the
 * raw wording before the Recruiter fills in the numbers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferTemplateResponseDto {
    private Long id;
    private String name;
    private int version;
    private String bodyTemplate;
    /** {@code null} for a company-wide template. */
    private Long departmentId;
}
