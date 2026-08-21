package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** for {@code POST /api/auth/login}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {

    @NotBlank(message = "{validation.auth.email.required}")
    private String email;

    @NotBlank(message = "{validation.auth.password.required}")
    private String password;
}
