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
import com.hirewise.be.dto.response.FileDownloadResponseDto;
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
    FileStorageService fileStorageService;

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

    /**
     * UC-20 file view: returns a short-lived URL to view or download one of an
     * Application's attached files directly from Cloud Storage (Google Drive
     * webViewLink / Dropbox temporary link).
     * <p>
     * Caller must have {@code APPLICATION_VIEW} scoped to the application's
     * department. If the file is still queued locally (BR-STORAGE-02),
     * {@link com.hirewise.be.exception.BadRequestException} is thrown with
     * {@link ErrorCode#FILE_NOT_YET_AVAILABLE}.
     *
     * @param applicationId id of the parent Application
     * @param fileId        id of the ApplicationFile record
     * @param currentUser   authenticated caller
     * @return a view/download URL valid for a short time (provider-dependent)
     * @throws ResourceNotFoundException if the application or file is not found,
     *                                   or the file does not belong to this application
     */
    @Transactional(readOnly = true)
    public String getFileViewUrl(UUID applicationId, Long fileId, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        JobPosition job = application.getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW,
                ResourceContext.job(job.getId(), departmentId));

        ApplicationFile applicationFile = applicationFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_FILE_NOT_FOUND, fileId));

        // Sanity check: the file must belong to the application in the URL.
        if (!applicationFile.getApplication().getId().equals(applicationId)) {
            throw new ResourceNotFoundException(ErrorCode.APPLICATION_FILE_NOT_FOUND, fileId);
        }

        return fileStorageService.getViewUrl(applicationFile.getFile());
    }

    @Transactional(readOnly = true)
    public FileDownloadResponseDto downloadApplicationFile(UUID applicationId, Long fileId, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        JobPosition job = application.getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW,
                ResourceContext.job(job.getId(), departmentId));

        ApplicationFile applicationFile = applicationFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_FILE_NOT_FOUND, fileId));

        // Sanity check: the file must belong to the application in the URL.
        if (!applicationFile.getApplication().getId().equals(applicationId)) {
            throw new ResourceNotFoundException(ErrorCode.APPLICATION_FILE_NOT_FOUND, fileId);
        }

        byte[] content = fileStorageService.downloadFile(applicationFile.getFile());
        return new FileDownloadResponseDto(
                applicationFile.getFile().getFileName(),
                applicationFile.getFile().getMimeType(),
                content
        );
    }
}
