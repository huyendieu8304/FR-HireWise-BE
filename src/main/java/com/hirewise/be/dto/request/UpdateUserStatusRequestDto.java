package com.hirewise.be.dto.request;

import com.hirewise.be.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** USER_UPDATE (chi HR_ADMIN) - khoa/mo khoa/vo hieu hoa tai khoan. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequestDto {

    @NotNull
    private UserStatus status;
}
