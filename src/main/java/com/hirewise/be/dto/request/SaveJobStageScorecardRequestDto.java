package com.hirewise.be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request body shared by "create" and "edit" of a (Job, Stage) Scorecard
 * (UC-27 step 3 - Hiring Manager configuring 1 Interview-type Stage). The
 * criteria list is always the COMPLETE, final set (not a delta) - see
 * {@code JobStageScorecardService} for how an edit that already has grading
 * history versions instead of overwriting in place (AF-01). {@code jobId}
 * and {@code pipelineStageId} come from the URL path, not this body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveJobStageScorecardRequestDto {

    @NotBlank(message = "Tên Scorecard không được để trống.")
    @Size(max = 255, message = "Tên Scorecard không được dài quá 255 ký tự.")
    private String name;

    /**
     * Optional - the Master Template this was cloned from as a starting
     * point (traceability only, "created from" display - never affects
     * behavior, so editing/archiving that Master Template later is always
     * safe). {@code null} if created entirely from scratch.
     */
    private UUID sourceMasterTemplateId;

    @NotEmpty(message = "Thêm ít nhất 1 tiêu chí trước khi lưu Scorecard.")
    @Valid
    private List<ScorecardCriterionInputDto> criteria;
}
