package com.hirewise.be.controller;

import com.hirewise.be.dto.response.RejectionReasonResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.RejectionReasonService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC-29 step 1: the standardized rejection-reason catalog.
 * <p>
 * RBAC: {@code GET /api/rejection-reasons} requires {@code APPLICATION_REJECT}
 * (RECRUITER only, per {@code role_permissions} - see V2) - no ownership
 * check, this is a global catalog, not scoped to any one Job.
 */
@RestController
@RequestMapping("/api/rejection-reasons")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class RejectionReasonController {

    RejectionReasonService rejectionReasonService;

    /**
     * Lists every active rejection reason, for the reject dropdown (UC-29 step 1).
     *
     * @param currentUser authenticated caller, used for authorization
     * @return active rejection reasons
     */
    @GetMapping
    public ResponseEntity<List<RejectionReasonResponseDto>> list(@CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(rejectionReasonService.listActive(currentUser));
    }
}
