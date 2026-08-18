package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccessScopeResponseDto {
    private Long id;
    private ScopeType scopeType;
    private Long departmentId;
    private String departmentName;
    private Long jobId;
    private boolean includeSubDepartments;
    private boolean canWrite;
    private Instant validFrom;
    private Instant validTo;
}
