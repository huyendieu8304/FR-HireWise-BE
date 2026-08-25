package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * result of submitting an application. {@link #duplicate}
 * tells the frontend which confirmation copy to show - ME-24 ("Nộp hồ sơ
 * thành công!") for a brand-new application, or ME-23 ("Bạn đã từng ứng
 * tuyển vị trí này...") when an existing application's CV was updated
 * instead of a new one being created.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitApplicationResponseDto {
    private UUID applicationId;
    private boolean duplicate;
}
