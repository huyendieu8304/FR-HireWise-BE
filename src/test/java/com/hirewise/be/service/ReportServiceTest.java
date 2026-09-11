package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ReportScopeResolver;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.dto.response.PipelineVelocityReportResponseDto;
import com.hirewise.be.dto.response.SourceRoiReportResponseDto;
import com.hirewise.be.repository.PublishingChannelRepository;
import com.hirewise.be.repository.ReportRepository;
import com.hirewise.be.repository.projection.ChannelTrafficRow;
import com.hirewise.be.repository.projection.SourceRoiRow;
import com.hirewise.be.repository.projection.StageVelocityRow;
import com.hirewise.be.repository.projection.TimeToHireRow;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UC-42 (Source ROI) va UC-43 (Pipeline Velocity).
 *
 * <p>Trong tam la nhung quyet dinh khong doc duoc tu SQL: ty le co mau qua nho
 * thi khong duoc xep hang, mau so bang 0 phai ra null chu khong phai 0, nguon
 * truc tiep va nguon la duoc gan nhan khac nhau, kenh co click ma khong co ho so
 * van phai co dong rieng, va co SLA thi SLA quyet dinh dau la Stage nghen.</p>
 *
 * <p>ReportMapper duoc dung that (khong mock) vi no chi la ham thuan tren cac
 * dong da aggregate - mock no thi test khong con kiem duoc phep tinh nao ca.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T03:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);
    private static final CurrentUser RECRUITER =
            new CurrentUser(7L, "rec@hirewise.vn", "Recruiter", Set.of("RECRUITER"));

    @Mock
    private AccessControlService accessControlService;
    @Mock
    private ReportScopeResolver reportScopeResolver;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PublishingChannelRepository publishingChannelRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(accessControlService, reportScopeResolver,
                reportRepository, publishingChannelRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sourceRoiChecksReportViewPermission() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(List.of());

        reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        verify(accessControlService).checkAccess(eq(RECRUITER), eq(PermissionCodes.REPORT_VIEW), isNull());
    }

    @Test
    void noJobInScope_returnsEmptyReportWithoutQuerying() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(List.of());

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        assertThat(report.getRows()).isEmpty();
        assertThat(report.getTotalApplications()).isZero();
        assertThat(report.getFromDate()).isEqualTo(FROM);
        assertThat(report.getToDate()).isEqualTo(TO);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void systemScope_queriesEveryJob() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(null);
        when(reportRepository.aggregateSourceRoi(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(reportRepository.aggregateChannelTraffic(anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(publishingChannelRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());

        reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        verify(reportRepository).aggregateSourceRoi(
                eq(Instant.parse("2026-06-01T00:00:00Z")),
                eq(Instant.parse("2026-07-01T00:00:00Z")),
                eq(true), anyList(), isNull(), isNull());
    }

    @Test
    void missingDates_defaultToTheLastNinetyDays() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(List.of());

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, null, null, null, null);

        assertThat(report.getToDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(report.getFromDate()).isEqualTo(LocalDate.of(2026, 6, 11));
    }

    @Test
    void backwardsRange_isSwappedRatherThanRejected() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(List.of());

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, TO, FROM, null, null);

        assertThat(report.getFromDate()).isEqualTo(FROM);
        assertThat(report.getToDate()).isEqualTo(TO);
    }

    @Test
    void sourceRoi_computesSharesRatesAndLabels() {
        stubScopeForOneJob();
        when(reportRepository.aggregateSourceRoi(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(
                        row("linkedin", 60, 6, 20.0),
                        row("", 40, 2, 30.0)));
        when(reportRepository.aggregateChannelTraffic(anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(traffic("linkedin", 4, 300)));
        when(publishingChannelRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(channel(PublishingChannelCode.LINKEDIN, "LinkedIn", "linkedin")));

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        assertThat(report.getTotalApplications()).isEqualTo(100);
        assertThat(report.getTotalHires()).isEqualTo(8);
        assertThat(report.getOverallHireRate()).isEqualByComparingTo("8.0");

        SourceRoiReportResponseDto.SourceRow linkedin = report.getRows().getFirst();
        assertThat(linkedin.getLabel()).isEqualTo("LinkedIn");
        assertThat(linkedin.getChannelCode()).isEqualTo(PublishingChannelCode.LINKEDIN);
        assertThat(linkedin.getApplicationShare()).isEqualByComparingTo("60.0");
        assertThat(linkedin.getHireRate()).isEqualByComparingTo("10.0");
        assertThat(linkedin.getClickCount()).isEqualTo(300);
        assertThat(linkedin.getClickToApplyRate()).isEqualByComparingTo("20.0");
        assertThat(linkedin.isDirect()).isFalse();

        SourceRoiReportResponseDto.SourceRow direct = report.getRows().get(1);
        assertThat(direct.isDirect()).isTrue();
        assertThat(direct.getLabel()).isEqualTo("Truy cập trực tiếp");

        // 6 hires at 20 days and 2 at 30 days is 22.5 days overall, not 25 -
        // the two per-source means must be weighted by their own hire counts.
        assertThat(report.getAvgDaysToHire()).isEqualByComparingTo("22.5");
    }

    @Test
    void sourceWithNoApplication_getsNullRateNotZero() {
        stubScopeForOneJob();
        when(reportRepository.aggregateSourceRoi(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(reportRepository.aggregateChannelTraffic(anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(traffic("facebook", 3, 120)));
        when(publishingChannelRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(channel(PublishingChannelCode.FACEBOOK, "Facebook", "facebook")));

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        // 120 clicks and not one application is the finding the dashboard exists
        // for, so the channel keeps its row instead of vanishing.
        assertThat(report.getRows()).hasSize(1);
        SourceRoiReportResponseDto.SourceRow facebook = report.getRows().getFirst();
        assertThat(facebook.getClickCount()).isEqualTo(120);
        assertThat(facebook.getApplicationCount()).isZero();
        assertThat(facebook.getHireRate()).isNull();
        assertThat(facebook.getApplicationShare()).isNull();
    }

    @Test
    void unknownUtmSource_keepsTheRawKeyAndIsFlagged() {
        stubScopeForOneJob();
        when(reportRepository.aggregateSourceRoi(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(row("old_campaign", 10, 1, 15.0)));
        when(reportRepository.aggregateChannelTraffic(anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(publishingChannelRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        SourceRoiReportResponseDto.SourceRow orphan = report.getRows().getFirst();
        assertThat(orphan.isUnknown()).isTrue();
        assertThat(orphan.getLabel()).isEqualTo("old_campaign");
        assertThat(orphan.getChannelCode()).isNull();
    }

    @Test
    void bestSource_ignoresSourcesBelowTheSampleFloor() {
        stubScopeForOneJob();
        when(reportRepository.aggregateSourceRoi(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(
                        row("linkedin", 50, 5, 20.0),
                        row("facebook", 2, 2, 12.0)));
        when(reportRepository.aggregateChannelTraffic(anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(publishingChannelRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(
                        channel(PublishingChannelCode.LINKEDIN, "LinkedIn", "linkedin"),
                        channel(PublishingChannelCode.FACEBOOK, "Facebook", "facebook")));

        SourceRoiReportResponseDto report = reportService.getSourceRoiReport(RECRUITER, FROM, TO, null, null);

        // Facebook is 2 of 2 - a perfect 100% built on two applications, which
        // is noise. LinkedIn wins on 10% of a real sample.
        assertThat(report.getBestSourceLabel()).isEqualTo("LinkedIn");
        assertThat(report.getBestSourceHireRate()).isEqualByComparingTo("10.0");
    }

    @Test
    void velocity_flagsTheSlaBreachAheadOfTheSlowestStage() {
        stubScopeForOneJob();
        when(reportRepository.aggregateStageVelocity(any(), any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(
                        stage("SCREENING", "Sàng lọc", 1, 48, 3.0, 20, 15, 5, 0),
                        stage("INTERVIEW", "Phỏng vấn", 2, null, 9.0, 20, 18, 2, 0)));
        when(reportRepository.aggregateTimeToHire(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(timeToHire(12, 24.4));

        PipelineVelocityReportResponseDto report =
                reportService.getPipelineVelocityReport(RECRUITER, FROM, TO, null, null);

        // Interview is slower in absolute terms, but Screening is the only stage
        // measured against a target and it misses it by a day - that is the
        // objective failure, so it takes the flag.
        assertThat(report.getBottleneckStageCode()).isEqualTo("SCREENING");
        PipelineVelocityReportResponseDto.StageRow screening = report.getStages().getFirst();
        assertThat(screening.getSlaDays()).isEqualByComparingTo("2.0");
        assertThat(screening.isSlaBreached()).isTrue();
        assertThat(screening.isBottleneck()).isTrue();
        assertThat(screening.getPassThroughRate()).isEqualByComparingTo("75.0");
        assertThat(report.getAvgTimeToHireDays()).isEqualByComparingTo("24.4");
        assertThat(report.getHiredCount()).isEqualTo(12);
    }

    @Test
    void velocity_slowestStageWinsOnlyWithEnoughSamples() {
        stubScopeForOneJob();
        when(reportRepository.aggregateStageVelocity(any(), any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(List.of(
                        stage("SCREENING", "Sàng lọc", 1, null, 4.0, 30, 25, 5, 0),
                        stage("OFFER", "Gửi Offer", 2, null, 40.0, 2, 2, 0, 0)));
        when(reportRepository.aggregateTimeToHire(any(), any(), anyBoolean(), anyList(), any(), any()))
                .thenReturn(timeToHire(0, null));

        PipelineVelocityReportResponseDto report =
                reportService.getPipelineVelocityReport(RECRUITER, FROM, TO, null, null);

        // Offer averages 40 days off two measurements. Two is not a pipeline
        // problem, it is two applications.
        assertThat(report.getBottleneckStageCode()).isEqualTo("SCREENING");
        assertThat(report.getAvgTimeToHireDays()).isNull();
    }

    @Test
    void velocity_noJobInScope_returnsEmptyStages() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(List.of());

        PipelineVelocityReportResponseDto report =
                reportService.getPipelineVelocityReport(RECRUITER, FROM, TO, null, null);

        assertThat(report.getStages()).isEmpty();
        assertThat(report.getBottleneckStageCode()).isNull();
        verify(reportRepository, never())
                .aggregateStageVelocity(any(), any(), any(), anyBoolean(), anyList(), any(), any());
    }

    private void stubScopeForOneJob() {
        when(reportScopeResolver.resolveVisibleJobIds(RECRUITER)).thenReturn(List.of(UUID.randomUUID()));
    }

    private static PublishingChannel channel(PublishingChannelCode code, String name, String utmSource) {
        PublishingChannel channel = new PublishingChannel();
        channel.setCode(code);
        channel.setName(name);
        channel.setUtmSource(utmSource);
        return channel;
    }

    private static SourceRoiRow row(String sourceKey, long applications, long hires, Double avgDaysToHire) {
        return new SourceRoiRow() {
            @Override
            public String getSourceKey() {
                return sourceKey;
            }

            @Override
            public long getApplicationCount() {
                return applications;
            }

            @Override
            public long getHireCount() {
                return hires;
            }

            @Override
            public Double getAvgDaysToHire() {
                return avgDaysToHire;
            }
        };
    }

    private static ChannelTrafficRow traffic(String utmSource, long shares, long clicks) {
        return new ChannelTrafficRow() {
            @Override
            public String getUtmSource() {
                return utmSource;
            }

            @Override
            public long getShareCount() {
                return shares;
            }

            @Override
            public long getClickCount() {
                return clicks;
            }
        };
    }

    private static TimeToHireRow timeToHire(long hired, Double avgDays) {
        return new TimeToHireRow() {
            @Override
            public long getHiredCount() {
                return hired;
            }

            @Override
            public Double getAvgDaysToHire() {
                return avgDays;
            }
        };
    }

    private static StageVelocityRow stage(String code, String name, int order, Integer slaHours,
                                          Double avgDays, long completed, long advanced,
                                          long rejected, long waiting) {
        return new StageVelocityRow() {
            @Override
            public String getStageCode() {
                return code;
            }

            @Override
            public String getStageName() {
                return name;
            }

            @Override
            public String getStageType() {
                return "SCREENING";
            }

            @Override
            public int getStageOrder() {
                return order;
            }

            @Override
            public Integer getSlaHours() {
                return slaHours;
            }

            @Override
            public long getCompletedCount() {
                return completed;
            }

            @Override
            public Double getAvgDays() {
                return avgDays;
            }

            @Override
            public Double getMedianDays() {
                return avgDays;
            }

            @Override
            public Double getP90Days() {
                return avgDays;
            }

            @Override
            public long getEnteredCount() {
                return completed + waiting;
            }

            @Override
            public long getAdvancedCount() {
                return advanced;
            }

            @Override
            public long getRejectedCount() {
                return rejected;
            }

            @Override
            public long getWaitingCount() {
                return waiting;
            }

            @Override
            public Double getMaxWaitingDays() {
                return waiting == 0 ? null : BigDecimal.TEN.doubleValue();
            }
        };
    }
}
