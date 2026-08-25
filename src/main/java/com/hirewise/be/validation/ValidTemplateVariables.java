package com.hirewise.be.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that every {@code {{Variable_Name}}} placeholder found in the
 * annotated {@link String} field belongs to the supported variable whitelist
 * (BR-EMAILTPL-02 / UC-10).
 *
 * <p>Supported variables: {@code Candidate_Name}, {@code Job_Title},
 * {@code Company}, {@code Recruiter_Name}, {@code Interview_Date},
 * {@code Interview_Time}, {@code Offer_Link}, {@code Booking_Link}.
 */
@Documented
@Constraint(validatedBy = TemplateVariableValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTemplateVariables {

    String message() default "{validation.email_template.variables.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
