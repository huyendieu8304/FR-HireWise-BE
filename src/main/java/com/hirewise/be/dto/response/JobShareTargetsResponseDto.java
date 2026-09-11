package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Everything the UC-31 share modal needs in one round trip: the Open Graph
 * card preview plus the channels currently on offer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobShareTargetsResponseDto {

    private SharePreviewResponseDto preview;

    /**
     * Only the channels HR Admin has enabled (UC-19). An empty list is the
     * UC-31 EX-01 state and the frontend renders it as "chưa bật kênh nào",
     * pointing HR Admin at the settings screen.
     */
    private List<ShareTargetResponseDto> channels;
}
