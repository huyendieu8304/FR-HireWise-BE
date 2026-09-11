package com.hirewise.be.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of the "Bang tieu chi" editable table (UC-27 Screen Description) -
 * used both when creating a template and when editing one (whole list
 * replaced each time, see {@code ScorecardTemplateService}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardCriterionInputDto {

    @NotBlank(message = "Tên tiêu chí không được để trống.")
    @Size(max = 255, message = "Tên tiêu chí không được dài quá 255 ký tự.")
    private String name;

    private String description;

    @NotNull(message = "Trọng số là bắt buộc.")
    @DecimalMin(value = "0.01", message = "Trọng số phải lớn hơn 0.")
    private BigDecimal weight;

    @NotNull(message = "Điểm tối đa là bắt buộc.")
    @DecimalMin(value = "0.01", message = "Điểm tối đa phải lớn hơn 0.")
    private BigDecimal maxScore;

    private boolean required;
}
