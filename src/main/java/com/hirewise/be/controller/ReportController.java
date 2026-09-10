package com.hirewise.be.controller;

import com.hirewise.be.dto.response.PipelineVelocityReportResponseDto;
import com.hirewise.be.dto.response.SourceRoiReportResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.ReportExportService;
import com.hirewise.be.service.ReportService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Module M20 - Reporting and Analytics. Two read-only dashboards, UC-42
 * (Source ROI) and UC-43 (Pipeline Velocity), sharing one filter shape
 * (BR-RPT-03: date range, department, Job Title).
 *
 * <p>No {@code @RequiresOwnership} and no {@code ResourceContext} here: a
 * report spans many jobs at once, so authorisation is the {@code REPORT_VIEW}
 * permission check inside {@link ReportService}, and the data itself is
 * narrowed by the Access Scope of the caller (BR-RPT-02).</p>
 *
 * <p>A filter that matches nothing returns 200 with empty rows, never a 4xx -
 * see EX-01/ME-37 on both use cases.</p>
 */
@RestController
@RequestMapping("/api/reports")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ReportController {

    ReportService reportService;
    ReportExportService reportExportService;

    /**
     * UC-42: share of applications, hire rate and channel traffic by source.
     *
     * @param fromDate      inclusive first day; defaults to 90 days before {@code toDate}
     * @param toDate        inclusive last day; defaults to today
     * @param departmentId  optional department filter
     * @param jobPositionId optional Job filter
     * @param currentUser   authenticated caller, must hold {@code REPORT_VIEW}
     * @return the Source ROI dashboard for the caller scope
     */
    @GetMapping("/source-roi")
    public ResponseEntity<SourceRoiReportResponseDto> sourceRoi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) UUID jobPositionId,
            @CurrentUserPrincipal CurrentUser currentUser) {

        return ResponseEntity.ok(reportService.getSourceRoiReport(
                currentUser, fromDate, toDate, departmentId, jobPositionId));
    }

    /**
     * UC-43: average, median and P90 time in each Stage, plus the bottleneck.
     *
     * @param fromDate      inclusive first day; defaults to 90 days before {@code toDate}
     * @param toDate        inclusive last day; defaults to today
     * @param departmentId  optional department filter
     * @param jobPositionId optional Job filter
     * @param currentUser   authenticated caller, must hold {@code REPORT_VIEW}
     * @return the Pipeline Velocity dashboard for the caller scope
     */
    @GetMapping("/pipeline-velocity")
    public ResponseEntity<PipelineVelocityReportResponseDto> pipelineVelocity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) UUID jobPositionId,
            @CurrentUserPrincipal CurrentUser currentUser) {

        return ResponseEntity.ok(reportService.getPipelineVelocityReport(
                currentUser, fromDate, toDate, departmentId, jobPositionId));
    }

    /**
     * UC-42 normal flow step 4 (BR-RPT-03): the same numbers as
     * {@link #sourceRoi} as a downloadable workbook.
     *
     * <p>The report is rebuilt server-side from the query parameters rather
     * than posted back from the browser, so the file can never disagree with
     * what the caller is allowed to see.</p>
     *
     * @param fromDate      inclusive first day; defaults to 90 days before {@code toDate}
     * @param toDate        inclusive last day; defaults to today
     * @param departmentId  optional department filter
     * @param jobPositionId optional Job filter
     * @param currentUser   authenticated caller, must hold {@code REPORT_VIEW}
     * @return an .xlsx attachment
     */
    @GetMapping("/source-roi/export")
    public ResponseEntity<byte[]> exportSourceRoi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) UUID jobPositionId,
            @CurrentUserPrincipal CurrentUser currentUser) {

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(
                currentUser, fromDate, toDate, departmentId, jobPositionId);

        return workbook(reportExportService.toSourceRoiWorkbook(report),
                reportExportService.fileName("source-roi", report.getToDate()));
    }

    /**
     * UC-43 (BR-RPT-03): the Pipeline Velocity table as a downloadable workbook.
     *
     * @param fromDate      inclusive first day; defaults to 90 days before {@code toDate}
     * @param toDate        inclusive last day; defaults to today
     * @param departmentId  optional department filter
     * @param jobPositionId optional Job filter
     * @param currentUser   authenticated caller, must hold {@code REPORT_VIEW}
     * @return an .xlsx attachment
     */
    @GetMapping("/pipeline-velocity/export")
    public ResponseEntity<byte[]> exportPipelineVelocity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) UUID jobPositionId,
            @CurrentUserPrincipal CurrentUser currentUser) {

        PipelineVelocityReportResponseDto report = reportService.getPipelineVelocityReport(
                currentUser, fromDate, toDate, departmentId, jobPositionId);

        return workbook(reportExportService.toPipelineVelocityWorkbook(report),
                reportExportService.fileName("pipeline-velocity", report.getToDate()));
    }

    /**
     * @param bytes    the serialised workbook
     * @param fileName name the browser saves the download under
     * @return the attachment response
     */
    private ResponseEntity<byte[]> workbook(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(bytes);
    }
}
