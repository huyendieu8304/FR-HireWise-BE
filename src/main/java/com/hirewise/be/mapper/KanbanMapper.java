package com.hirewise.be.mapper;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.dto.response.ApplicationCardResponseDto;
import com.hirewise.be.dto.response.KanbanBoardResponseDto;
import com.hirewise.be.dto.response.KanbanStageColumnResponseDto;
import com.hirewise.be.dto.response.MoveApplicationStageResponseDto;

import java.util.List;

/**
 * Converts UC-22/UC-23 (Kanban Pipeline) entities into their response DTOs.
 */
public final class KanbanMapper {

    private KanbanMapper() {
    }

    /**
     * Converts an {@link Application} into its Kanban card DTO.
     *
     * @param application entity to convert; {@code candidate} must already be loaded
     * @return the corresponding card DTO
     */
    public static ApplicationCardResponseDto toCardDto(Application application) {
        return ApplicationCardResponseDto.builder()
                .applicationId(application.getId())
                .candidateId(application.getCandidate().getId())
                .candidateName(application.getCandidate().getFullName())
                .candidateEmail(application.getCandidate().getPrimaryEmail())
                .candidatePhone(application.getCandidate().getPhone())
                .status(application.getStatus())
                .appliedAt(application.getAppliedAt())
                .lastStageChangedAt(application.getLastStageChangedAt())
                .aiMatchScore(application.getAiMatchScore())
                .build();
    }

    /**
     * Converts a {@link PipelineStage} plus its current Applications into
     * one Kanban column DTO.
     *
     * @param stage        stage entity to convert
     * @param applications applications currently at this stage, resolved by the caller
     * @return the corresponding column DTO
     */
    public static KanbanStageColumnResponseDto toColumnDto(PipelineStage stage, List<Application> applications) {
        return KanbanStageColumnResponseDto.builder()
                .stageId(stage.getId())
                .name(stage.getName())
                .code(stage.getCode())
                .stageType(stage.getStageType())
                .position(stage.getPosition())
                .terminal(stage.isTerminal())
                .applications(applications.stream().map(KanbanMapper::toCardDto).toList())
                .build();
    }

    /**
     * Assembles the full board DTO for one Job.
     *
     * @param job     job position the board belongs to
     * @param columns columns already converted via {@link #toColumnDto}, in position order
     * @return the corresponding board DTO
     */
    public static KanbanBoardResponseDto toBoardDto(JobPosition job, List<KanbanStageColumnResponseDto> columns) {
        return KanbanBoardResponseDto.builder()
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .columns(columns)
                .build();
    }

    /**
     * Converts an Application right after UC-23 moved it, into the move
     * response DTO.
     *
     * @param application  the application, already updated to its new {@code currentStage}
     * @param fromStageId  id of the stage it was moved out of
     * @return the corresponding move-result DTO
     */
    public static MoveApplicationStageResponseDto toMoveResponseDto(Application application, Long fromStageId) {
        return MoveApplicationStageResponseDto.builder()
                .applicationId(application.getId())
                .fromStageId(fromStageId)
                .toStageId(application.getCurrentStage().getId())
                .status(application.getStatus())
                .lastStageChangedAt(application.getLastStageChangedAt())
                .build();
    }
}
