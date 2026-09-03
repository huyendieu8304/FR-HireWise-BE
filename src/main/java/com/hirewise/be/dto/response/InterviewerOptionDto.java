package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO representing an interviewer option for interview assignment (UC-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewerOptionDto {
    private Long id;
    private String fullName;
    private String email;
    private String departmentName;
}
