package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.dto.response.RejectionReasonResponseDto;
import com.hirewise.be.mapper.ApplicationMapper;
import com.hirewise.be.repository.RejectionReasonRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UC-29 step 1: the standardized rejection-reason catalog (BR-REJ-01) a
 * Recruiter picks from before rejecting an Application.
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class RejectionReasonService {

    RejectionReasonRepository rejectionReasonRepository;
    AccessControlService accessControlService;

    /**
     * Lists every active rejection reason, alphabetical by label - the
     * choices shown in the reject dropdown.
     *
     * @param currentUser authenticated caller, must have {@code APPLICATION_REJECT}
     * @return active rejection reasons
     */
    public List<RejectionReasonResponseDto> listActive(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_REJECT, ResourceContext.none());
        return rejectionReasonRepository.findByActiveTrueOrderByLabelAsc().stream()
                .map(ApplicationMapper::toReasonDto)
                .toList();
    }
}
