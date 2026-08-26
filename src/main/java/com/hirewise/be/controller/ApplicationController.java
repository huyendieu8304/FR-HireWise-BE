package com.hirewise.be.controller;

import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.RequiresOwnership;
import com.hirewise.be.dto.request.MoveApplicationStageRequestDto;
import com.hirewise.be.dto.response.MoveApplicationStageResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.KanbanService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UC-23: Kanban drag-and-drop stage transition for one Application.
 * <p>
 * RBAC: {@code PATCH /api/applications/{applicationId}/stage} requires
 * {@code APPLICATION_MOVE_STAGE} (scoped to the job's department) AND
 * ownership of the parent Job as its assigned Recruiter (RBAC.md section 4:
 * {@code application.job.recruiter_id}) - both enforced by
 * {@link RequiresOwnership}/{@code OwnershipAspect} before this method body runs.
 */
@RestController
@RequestMapping("/api/applications")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ApplicationController {

    KanbanService kanbanService;

    /**
     * UC-23 main flow: moves an Application to a different Kanban column
     * (Pipeline Stage) and records the change (BR-KANBAN-01).
     *
     * @param applicationId id of the application being moved
     * @param request       target stage id
     * @param currentUser   authenticated caller - must own the parent Job (as its Recruiter)
     * @return the application's new stage/status/timestamp
     */
    @PatchMapping("/{applicationId}/stage")
    @RequiresOwnership(resourceType = "APPLICATION", idParam = "applicationId",
            permission = PermissionCodes.APPLICATION_MOVE_STAGE)
    public ResponseEntity<MoveApplicationStageResponseDto> moveStage(
            @PathVariable UUID applicationId,
            @Valid @RequestBody MoveApplicationStageRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(kanbanService.moveStage(applicationId, request, currentUser));
    }
}
