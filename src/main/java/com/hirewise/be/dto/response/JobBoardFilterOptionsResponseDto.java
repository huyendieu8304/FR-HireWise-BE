package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * UC-16 REF 2: options for the public Job Board's filter dropdowns. Kept as
 * its own public endpoint (rather than reusing the authenticated
 * {@code /api/departments}) so an anonymous candidate's browser never needs
 * to call anything but {@code /api/public/**}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobBoardFilterOptionsResponseDto {
    private List<DepartmentResponseDto> departments;
    private List<EmploymentType> employmentTypes;
}
