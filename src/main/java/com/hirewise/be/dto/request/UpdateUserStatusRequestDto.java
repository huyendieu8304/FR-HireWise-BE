package com.hirewise.be.dto.request;

import com.hirewise.be.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for USER_UPDATE (HR_ADMIN only) - locks/unlocks/deactivates
 * a user account.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequestDto {

    @NotNull(message = "{validation.user.status.required}")
    private UserStatus status;
}
