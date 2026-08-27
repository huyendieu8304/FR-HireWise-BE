package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationRejection;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.dto.response.ApplicationDetailResponseDto;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.ApplicationMapper;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRejectionRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * UC-20: the Applicant Card - full detail of one Candidate's Application,
 * beyond what the Kanban card ({@code KanbanService#getBoard}) already
 * shows.
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ApplicationService {

    ApplicationRepository applicationRepository;
    ApplicationFileRepository applicationFileRepository;
    ApplicationStageHistoryRepository applicationStageHistoryRepository;
    ApplicationRejectionRepository applicationRejectionRepository;
    AccessControlService accessControlService;

    /**
     * UC-20 main flow: assembles the Applicant Card - candidate contact/status
     * info, current stage, attached files, the full stage-change timeline
     * and, if the application was rejected (UC-29), its rejection record.
     *
     * @param applicationId id of the application
     * @param currentUser   authenticated caller, must have {@code APPLICATION_VIEW}
     *                      scoped to the application's job's department
     * @return the Applicant Card detail
     * @throws ResourceNotFoundException if no application exists with this id
     */
    @Transactional(readOnly = true)
    public ApplicationDetailResponseDto getDetail(UUID applicationId, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        JobPosition job = application.getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW,
                ResourceContext.job(job.getId(), departmentId));

        List<ApplicationFile> files = applicationFileRepository.findByApplication_Id(applicationId);
        List<ApplicationStageHistory> history =
                applicationStageHistoryRepository.findByApplication_IdOrderByChangedAtAsc(applicationId);
        ApplicationRejection rejection =
                applicationRejectionRepository.findByApplication_Id(applicationId).orElse(null);

        return ApplicationMapper.toDetailDto(application, files, history, rejection);
    }
}
