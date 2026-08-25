package com.hirewise.be.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConstraintValidator for {@link ValidTemplateVariables} (BR-EMAILTPL-02 / UC-10).
 *
 * <p>Scans the annotated field value for every {@code {{...}}} placeholder using a
 * regex and rejects the field if any placeholder name is not in
 * {@link #SUPPORTED_VARIABLES}.
 *
 * <p>A {@code null} or blank value is treated as valid here; other annotations
 * ({@code @NotBlank}) handle the "must not be empty" constraint.
 */
public class TemplateVariableValidator implements ConstraintValidator<ValidTemplateVariables, String> {

    /** All dynamic variable names supported by the email rendering engine (UC-10 spec + full EM-01..EM-13 seed). */
    static final Set<String> SUPPORTED_VARIABLES = Set.of(
            // Ung vien
            "Candidate_Name", "Full_Name",
            // Tuyen dung / Job
            "Job_Title", "Company", "Department_Name", "Openings", "Role_Name",
            // Nguoi dung he thong
            "Recruiter_Name", "Manager_Name", "Interviewer_Name",
            // Phong van
            "Interview_Date", "Interview_Time", "Interview_Mode",
            "Meeting_Location_Or_Link", "Confirm_Link", "Booking_Link",
            "Expiry_Hours", "Scorecard_Link", "Candidate_Profile_Link",
            // Offer / Onboarding
            "Offer_Link", "Expiry_Date", "Signed_At", "Signed_File_Link", "Start_Date",
            // Link / Phe duyet
            "Activation_Link", "Job_Approval_Link", "Job_Link", "Dashboard_Link",
            // Noi dung dong
            "Decision", "Applied_At", "Reject_Reason_Block",
            "Custom_Message_Block", "Channel_Status_List", "Breach_List",
            // SLA / He thong
            "n", "Stage_Name"
    );

    /** Matches {{ any content that is not a closing brace }} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // delegate to @NotBlank
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        List<String> unsupported = new ArrayList<>();

        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            if (!SUPPORTED_VARIABLES.contains(varName)) {
                unsupported.add(varName);
            }
        }

        if (unsupported.isEmpty()) {
            return true;
        }

        // Override the default message to include the offending variable names
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                "Template contains unsupported variable(s): " + unsupported +
                ". Supported: Candidate_Name, Job_Title, Company, Recruiter_Name, " +
                "Interview_Date, Interview_Time, Offer_Link, Booking_Link (BR-EMAILTPL-02)."
        ).addConstraintViolation();

        return false;
    }
}
