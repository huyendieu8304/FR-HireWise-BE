package com.hirewise.be.controller;

import com.hirewise.be.dto.response.AiScreeningBatchResponseDto;
import com.hirewise.be.dto.response.KanbanBoardResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.AiScreeningService;
import com.hirewise.be.service.KanbanService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UC-22: the Kanban board of Applications for one Job Position.
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET /api/jobs/{jobId}/kanban-board} - {@code APPLICATION_VIEW}, scoped
 *   to the job's department (Recruiter/Hiring Manager/Interviewer, see RBAC.md).</li>
 *   <li>{@code POST /api/jobs/{jobId}/kanban-board/stages/{stageId}/ai-screening/run-batch}
 *   - {@code AI_VIEW}, scoped to the job's department (UC-21 "Quét cả cột").</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs/{jobId}/kanban-board")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class KanbanController {

    KanbanService kanbanService;
    AiScreeningService aiScreeningService;

    /**
     * UC-22 main flow: every active Stage of the job's Pipeline Template,
     * each with the Applications currently sitting in it.
     *
     * @param jobId       id of the job position
     * @param currentUser authenticated caller, used for authorization
     * @return the Kanban board
     */
    @GetMapping
    public ResponseEntity<KanbanBoardResponseDto> getBoard(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(kanbanService.getBoard(jobId, currentUser));
    }

    /**
     * UC-21 "Quét cả cột": AI Screening không còn tự động chạy khi Candidate
     * nộp hồ sơ nữa (xem {@code JobApplicationService#apply}) - Recruiter
     * chủ động bấm nút này trên 1 cột (thường là cột "Mới"/{@code INTAKE})
     * để enqueue AI Screening Run cho mọi Application đang ở Stage đó cùng
     * lúc, thay vì bấm "Phân tích lại" từng ứng viên một
     * ({@code POST /applications/{id}/ai-screening/run}).
     *
     * @param jobId       id of the job position (Kanban board đang xem)
     * @param stageId     id of the pipeline stage (cột) muốn quét
     * @param currentUser authenticated caller, used for authorization
     * @return 202 Accepted kèm số lượng queued/skipped - các run PENDING được
     *         {@code event.AiScreeningDispatcher} gọi Claude API bất đồng bộ
     */
    @PostMapping("/stages/{stageId}/ai-screening/run-batch")
    public ResponseEntity<AiScreeningBatchResponseDto> runAiScreeningBatch(
            @PathVariable UUID jobId,
            @PathVariable Long stageId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.accepted().body(aiScreeningService.runBatchForStage(jobId, stageId, currentUser));
    }
}
