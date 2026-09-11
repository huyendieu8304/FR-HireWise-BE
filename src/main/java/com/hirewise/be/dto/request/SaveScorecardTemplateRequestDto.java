package com.hirewise.be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body shared by "create" and "edit" for a Master Scorecard
 * Template (UC-27, HR Admin's library - always company-wide, never
 * Job/Stage-scoped) - the criteria list is always the COMPLETE, final set
 * (not a delta). Always edited in place (no versioning - see
 * {@code ScorecardTemplate}'s own Javadoc for why).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveScorecardTemplateRequestDto {

    @NotBlank(message = "Tên Template không được để trống.")
    @Size(max = 255, message = "Tên Template không được dài quá 255 ký tự.")
    private String name;

    @NotEmpty(message = "Thêm ít nhất 1 tiêu chí trước khi lưu Template.")
    @Valid
    private List<ScorecardCriterionInputDto> criteria;
}
