package com.hirewise.be.mapper;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationRejection;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.RejectionReason;
import com.hirewise.be.dto.response.ApplicationDetailResponseDto;
import com.hirewise.be.dto.response.ApplicationFileResponseDto;
import com.hirewise.be.dto.response.ApplicationRejectionResponseDto;
import com.hirewise.be.dto.response.ApplicationStageHistoryResponseDto;
import com.hirewise.be.dto.response.RejectionReasonResponseDto;

import java.util.List;

/**
 * Converts UC-20 (Applicant Card) and UC-29 (Reject Application) entities
 * into their response DTOs.
 */
public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    /**
     * Assembles the full Applicant Card DTO (UC-20).
     *
     * @param application  entity to convert; {@code candidate}, {@code jobPosition}
     *                     and {@code currentStage} must already be loaded
     * @param files        this application's attached files, already resolved by the caller
     * @param stageHistory this application's stage-change timeline, oldest first
     * @param rejection    this application's rejection record, or {@code null} if never rejected
     * @return the corresponding Applicant Card detail DTO
     */
    public static ApplicationDetailResponseDto toDetailDto(
            Application application,
            List<ApplicationFile> files,
            List<ApplicationStageHistory> stageHistory,
            ApplicationRejection rejection) {
        return ApplicationDetailResponseDto.builder()
                .applicationId(application.getId())
                .candidateId(application.getCandidate().getId())
                .candidateName(application.getCandidate().getFullName())
                .candidateEmail(application.getCandidate().getPrimaryEmail())
                .candidatePhone(application.getCandidate().getPhone())
                .candidateStatus(application.getCandidate().getStatus())
                .jobId(application.getJobPosition().getId())
                .jobTitle(application.getJobPosition().getTitle())
                .currentStageId(application.getCurrentStage().getId())
                .currentStageName(application.getCurrentStage().getName())
                .currentStageType(application.getCurrentStage().getStageType())
                .currentStageTerminal(application.getCurrentStage().isTerminal())
                .status(application.getStatus())
                .appliedAt(application.getAppliedAt())
                .lastStageChangedAt(application.getLastStageChangedAt())
                .files(files.stream().map(ApplicationMapper::toFileDto).toList())
                .stageHistory(stageHistory.stream().map(ApplicationMapper::toHistoryDto).toList())
                .rejection(rejection != null ? toRejectionDto(rejection) : null)
                .build();
    }

    /** Converts one {@link ApplicationFile} into its response DTO. */
    public static ApplicationFileResponseDto toFileDto(ApplicationFile file) {
        return ApplicationFileResponseDto.builder()
                .fileId(file.getId())
                .fileName(file.getFile().getFileName())
                .mimeType(file.getFile().getMimeType())
                .sizeBytes(file.getFile().getSizeBytes())
                .fileRole(file.getFileRole())
                .primary(file.isPrimary())
                .build();
    }

    /** Converts one {@link ApplicationStageHistory} row into its timeline entry DTO. */
    public static ApplicationStageHistoryResponseDto toHistoryDto(ApplicationStageHistory history) {
        return ApplicationStageHistoryResponseDto.builder()
                .fromStageId(history.getFromStage() != null ? history.getFromStage().getId() : null)
                .fromStageName(history.getFromStage() != null ? history.getFromStage().getName() : null)
                .toStageId(history.getToStage().getId())
                .toStageName(history.getToStage().getName())
                .transitionType(history.getTransitionType())
                .changedByName(history.getChangedBy() != null ? history.getChangedBy().getFullName() : null)
                .changedAt(history.getChangedAt())
                .build();
    }

    /** Converts an {@link ApplicationRejection} into its response DTO. */
    public static ApplicationRejectionResponseDto toRejectionDto(ApplicationRejection rejection) {
        return ApplicationRejectionResponseDto.builder()
                .reasonId(rejection.getReason().getId())
                .reasonCode(rejection.getReason().getCode())
                .reasonLabel(rejection.getReason().getLabel())
                .customMessage(rejection.getCustomMessage())
                .rejectedByName(rejection.getRejectedBy() != null ? rejection.getRejectedBy().getFullName() : null)
                .rejectedAt(rejection.getRejectedAt())
                .build();
    }

    /** Converts one {@link RejectionReason} catalog entry into its response DTO (UC-29 step 1). */
    public static RejectionReasonResponseDto toReasonDto(RejectionReason reason) {
        return RejectionReasonResponseDto.builder()
                .id(reason.getId())
                .code(reason.getCode())
                .label(reason.getLabel())
                .category(reason.getCategory())
                .build();
    }
}
