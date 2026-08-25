package com.hirewise.be.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body (multipart form fields) for UC-17 step 2 - the CV file
 * itself is bound separately as a {@code MultipartFile} request param (see
 * {@code controller.PublicJobBoardController#apply}), not a field here, so
 * that a malformed/oversized file and a missing text field surface as two
 * clearly distinct problems.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitApplicationRequestDto {

    @NotBlank(message = "{validation.application.full_name.required}")
    @Size(max = 150, message = "{validation.application.full_name.size}")
    private String fullName;

    @NotBlank(message = "{validation.application.email.required}")
    @Email(message = "{validation.application.email.invalid}")
    @Size(max = 255, message = "{validation.application.email.size}")
    private String email;

    /** BR: Vietnamese mobile format, optionally prefixed with +84 instead of the leading 0. */
    @NotBlank(message = "{validation.application.phone.required}")
    @Pattern(regexp = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$", message = "{validation.application.phone.invalid}")
    private String phone;
}
