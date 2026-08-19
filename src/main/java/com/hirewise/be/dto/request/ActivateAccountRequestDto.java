package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sets the password for an Invited account, or resets it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivateAccountRequestDto {

    @NotBlank(message = "{validation.auth.token.required}")
    private String token;


    //password must be at least 8 characters and contain an uppercase letter, a lowercase letter and a digit.
    @NotBlank(message = "{validation.auth.password.required}")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
            message = "{validation.auth.password.weak}")
    private String password;
}
