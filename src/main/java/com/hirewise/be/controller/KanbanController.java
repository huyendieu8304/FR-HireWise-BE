package com.hirewise.be.controller;

import com.hirewise.be.dto.response.KanbanBoardResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.KanbanService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs/{jobId}/kanban-board")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class KanbanController {

    KanbanService kanbanService;

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
}
