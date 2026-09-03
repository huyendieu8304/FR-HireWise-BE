package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interview participant response DTO (UC-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewParticipantDto {
    private Long interviewerId;
    private String interviewerName;
    private String interviewerEmail;
}
