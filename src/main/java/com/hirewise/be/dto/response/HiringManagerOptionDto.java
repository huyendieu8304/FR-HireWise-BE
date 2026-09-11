package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO representing a Hiring Manager option for the Recruiter to
 * pick from when creating/editing a Job Position (UC-12) - mirrors
 * {@link InterviewerOptionDto}'s shape for the analogous "pick 1 active user
 * holding role X" pattern (UC-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HiringManagerOptionDto {
    private Long id;
    private String fullName;
    private String email;
    private String departmentName;
}
