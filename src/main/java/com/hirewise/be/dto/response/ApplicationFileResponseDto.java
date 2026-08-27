package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ApplicationFileRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-20: one file attached to an Application (CV, cover letter, portfolio)
 * shown on the Applicant Card. Binary content lives on Cloud Storage - only
 * metadata is exposed here (see {@code domain.StoredFile}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationFileResponseDto {
    private Long fileId;
    private String fileName;
    private String mimeType;
    private long sizeBytes;
    private ApplicationFileRole fileRole;
    private boolean primary;
}
