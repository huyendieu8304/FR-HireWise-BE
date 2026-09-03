package com.hirewise.be.dto.request;

import com.hirewise.be.domain.InterviewMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Request body for scheduling an interview session (UC-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleInterviewRequestDto {

    @NotNull(message = "{validation.interview.target_stage_id.not_null}")
    private Long targetStageId;

    @NotEmpty(message = "{validation.interview.interviewer_ids.not_empty}")
    private List<@NotNull Long> interviewerIds;

    @NotNull(message = "{validation.interview.interview_date.not_null}")
    private LocalDate interviewDate;

    @NotNull(message = "{validation.interview.interview_time.not_null}")
    private LocalTime interviewTime;

    @NotNull(message = "{validation.interview.mode.not_null}")
    private InterviewMode mode;

    private String locationOrLink;

    private String notes;
}
