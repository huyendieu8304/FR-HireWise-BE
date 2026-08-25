package com.hirewise.be.controller;

import com.hirewise.be.domain.EmploymentType;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.SubmitApplicationRequestDto;
import com.hirewise.be.dto.response.JobBoardDetailResponseDto;
import com.hirewise.be.dto.response.JobBoardFilterOptionsResponseDto;
import com.hirewise.be.dto.response.JobBoardSummaryResponseDto;
import com.hirewise.be.dto.response.SubmitApplicationResponseDto;
import com.hirewise.be.service.JobApplicationService;
import com.hirewise.be.service.PublicJobBoardService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * UC-16 (public Job Board) and UC-17 (apply with CV). Every endpoint here
 * lives under {@code /api/public/jobs/**} and is reachable without
 * authentication - candidates browsing/applying are, by definition,
 * anonymous. See {@code SecurityConfig} for the exact permitAll rules
 * (GET is blanket-permitted under {@code /api/public/**}; the apply POST
 * needs its own explicit rule since writes aren't covered by that pattern).
 */
@RestController
@RequestMapping("/api/public/jobs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublicJobBoardController {

    PublicJobBoardService publicJobBoardService;
    JobApplicationService jobApplicationService;

    /**
     * UC-16 step 2-3: card list of Published jobs, optionally filtered.
     *
     * @param departmentId   optional department filter
     * @param employmentType optional employment type filter
     * @param keyword        optional keyword, matched against the job title
     * @param page           zero-based page index (defaults to 0)
     * @param size           page size (defaults to 10, capped between 1 and 50)
     * @return a page of jobs, newest first
     */
    @GetMapping
    public ResponseEntity<PagedResponseDto<JobBoardSummaryResponseDto>> list(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        // Same clamping convention as UserAdminController#search - an anonymous,
        // public endpoint is an even more obvious DoS target for an unbounded page size.
        int boundedSize = Math.min(Math.max(size, 1), 50);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(publicJobBoardService.list(departmentId, employmentType, keyword, pageable));
    }

    /**
     * UC-16 REF 2: options for the filter dropdowns.
     *
     * @return departments (with a Published job) and the fixed employment type list
     */
    @GetMapping("/filter-options")
    public ResponseEntity<JobBoardFilterOptionsResponseDto> filterOptions() {
        return ResponseEntity.ok(publicJobBoardService.filterOptions());
    }

    /**
     * UC-16 step 4: full JD for a single Published job.
     *
     * @param jobId job position id
     * @return the job detail (404 if it doesn't exist or isn't Published)
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobBoardDetailResponseDto> detail(@PathVariable UUID jobId) {
        return ResponseEntity.ok(publicJobBoardService.getPublishedDetail(jobId));
    }

    /**
     * UC-17: submit an application with contact info + CV. Multipart form
     * (not JSON) since it carries a binary file alongside plain text
     * fields; {@code request} binds the text fields with Bean Validation
     * (EX-02/ME-01), {@code cvFile} is validated separately in the service
     * (EX-01/ME-22) since "wrong file" and "missing field" are different
     * kinds of problems worth reporting distinctly.
     *
     * @param jobId    the Published job being applied to
     * @param request  contact info
     * @param cvFile   the CV file (.pdf/.doc/.docx, max 10MB)
     * @return the created/updated application's id
     */
    @PostMapping(path = "/{jobId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitApplicationResponseDto> apply(
            @PathVariable UUID jobId,
            @Valid @ModelAttribute SubmitApplicationRequestDto request,
            @RequestParam("cvFile") MultipartFile cvFile) {
        SubmitApplicationResponseDto response = jobApplicationService.apply(jobId, request, cvFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
