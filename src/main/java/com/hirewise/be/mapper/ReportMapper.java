package com.hirewise.be.mapper;

import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.dto.response.SourceRoiReportResponseDto;
import com.hirewise.be.repository.projection.ChannelTrafficRow;
import com.hirewise.be.repository.projection.SourceRoiRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the raw aggregation rows of module M20 into the DTOs the dashboards
 * render (UC-42, UC-43). Pure arithmetic and labelling only - no queries, no
 * service calls.
 */
public final class ReportMapper {

    /** Label for applications that arrived with no {@code utm_source} at all. */
    public static final String DIRECT_LABEL = "Truy cập trực tiếp";

    /** Every ratio and duration is reported to one decimal, which is all a dashboard can use. */
    private static final int SCALE = 1;

    private ReportMapper() {
    }

    /**
     * UC-42 EX-01: the shape returned when the filter matches nothing, so the
     * front end can render ME-37 off a normal 200 response.
     *
     * @param fromDate inclusive first day of the resolved range
     * @param toDate   inclusive last day of the resolved range
     * @return a report with no rows and no totals
     */
    public static SourceRoiReportResponseDto emptySourceRoi(LocalDate fromDate, LocalDate toDate) {
        return SourceRoiReportResponseDto.builder()
                .rows(List.of())
                .totalApplications(0)
                .totalHires(0)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
    }

    /**
     * UC-42: assembles the Source ROI dashboard.
     *
     * <p>Channels that were shared but never produced an application still get
     * a row, with zeroes in the application columns. That combination - real
     * clicks, no applications - is precisely the finding the dashboard exists
     * to surface, and dropping the row would hide it.</p>
     *
     * @param sourceRows          per-source aggregates over the selected cohort
     * @param trafficByUtmSource  lifetime share/click counters, keyed by {@code utm_source}
     * @param channelsByUtmSource configured channels, keyed by {@code utm_source}
     * @param fromDate            inclusive first day of the resolved range
     * @param toDate              inclusive last day of the resolved range
     * @param minSampleSize       applications a source needs before it may be
     *                            named the best performer
     * @return the fully computed report
     */
    public static SourceRoiReportResponseDto toSourceRoiReport(
            List<SourceRoiRow> sourceRows,
            Map<String, ChannelTrafficRow> trafficByUtmSource,
            Map<String, PublishingChannel> channelsByUtmSource,
            LocalDate fromDate,
            LocalDate toDate,
            int minSampleSize) {

        long totalApplications = sourceRows.stream().mapToLong(SourceRoiRow::getApplicationCount).sum();
        long totalHires = sourceRows.stream().mapToLong(SourceRoiRow::getHireCount).sum();

        List<SourceRoiReportResponseDto.SourceRow> rows = new ArrayList<>();
        Set<String> seenSourceKeys = new HashSet<>();

        for (SourceRoiRow row : sourceRows) {
            String sourceKey = row.getSourceKey();
            seenSourceKeys.add(sourceKey);
            ChannelTrafficRow traffic = trafficByUtmSource.get(sourceKey);
            rows.add(toSourceRow(sourceKey, row.getApplicationCount(), row.getHireCount(),
                    row.getAvgDaysToHire(), traffic, channelsByUtmSource.get(sourceKey),
                    totalApplications));
        }

        trafficByUtmSource.forEach((utmSource, traffic) -> {
            if (seenSourceKeys.contains(utmSource)) {
                return;
            }
            rows.add(toSourceRow(utmSource, 0L, 0L, null, traffic,
                    channelsByUtmSource.get(utmSource), totalApplications));
        });

        // Weighted mean: each row already averages over its own hires, so
        // re-weighting by hireCount reproduces the exact cohort-wide figure
        // without a second trip to the database.
        BigDecimal avgDaysToHire = weightedAverageDaysToHire(sourceRows, totalHires);

        SourceRoiReportResponseDto.SourceRow best = rows.stream()
                .filter(row -> row.getApplicationCount() >= minSampleSize)
                .filter(row -> row.getHireRate() != null)
                .max(Comparator.comparing(SourceRoiReportResponseDto.SourceRow::getHireRate))
                .orElse(null);

        return SourceRoiReportResponseDto.builder()
                .rows(rows)
                .totalApplications(totalApplications)
                .totalHires(totalHires)
                .overallHireRate(percentage(totalHires, totalApplications))
                .bestSourceLabel(best == null ? null : best.getLabel())
                .bestSourceHireRate(best == null ? null : best.getHireRate())
                .avgDaysToHire(avgDaysToHire)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
    }

    /**
     * Builds one dashboard row, resolving what to call the source.
     *
     * <p>Three cases, and the difference matters: the empty key is direct
     * traffic; a key a configured channel claims gets that channel name; a key
     * nothing claims is shown raw and flagged unknown. The third case is normal
     * rather than a bug - editing a {@code utm_source} in UC-19 deliberately
     * leaves past attribution untouched instead of rewriting history.</p>
     *
     * @param sourceKey         raw {@code utm_source}, empty for direct traffic
     * @param applicationCount  applications from this source in the cohort
     * @param hireCount         of those, how many were hired
     * @param avgDaysToHire     mean days to hire, or {@code null} with no hire
     * @param traffic           lifetime counters, or {@code null} if never shared
     * @param channel           the channel claiming this key, or {@code null}
     * @param totalApplications cohort size, for the share percentage
     * @return the row as the dashboard shows it
     */
    private static SourceRoiReportResponseDto.SourceRow toSourceRow(String sourceKey,
                                                                    long applicationCount,
                                                                    long hireCount,
                                                                    Double avgDaysToHire,
                                                                    ChannelTrafficRow traffic,
                                                                    PublishingChannel channel,
                                                                    long totalApplications) {
        boolean direct = sourceKey == null || sourceKey.isBlank();
        boolean unknown = !direct && channel == null;
        String label = direct ? DIRECT_LABEL : (channel != null ? channel.getName() : sourceKey);

        long shareCount = traffic == null ? 0L : traffic.getShareCount();
        long clickCount = traffic == null ? 0L : traffic.getClickCount();

        return SourceRoiReportResponseDto.SourceRow.builder()
                .sourceKey(sourceKey)
                .channelCode(channel == null ? null : channel.getCode())
                .label(label)
                .applicationCount(applicationCount)
                .applicationShare(percentage(applicationCount, totalApplications))
                .hireCount(hireCount)
                .hireRate(percentage(hireCount, applicationCount))
                .shareCount(shareCount)
                .clickCount(clickCount)
                .clickToApplyRate(percentage(applicationCount, clickCount))
                .avgDaysToHire(scaled(avgDaysToHire))
                .direct(direct)
                .unknown(unknown)
                .build();
    }

    /**
     * Cohort-wide mean days to hire, rebuilt from the per-source means.
     *
     * @param sourceRows per-source aggregates
     * @param totalHires total hires across those rows
     * @return the weighted mean, or {@code null} when nobody was hired
     */
    private static BigDecimal weightedAverageDaysToHire(List<SourceRoiRow> sourceRows, long totalHires) {
        if (totalHires == 0) {
            return null;
        }
        double weightedSum = sourceRows.stream()
                .filter(row -> row.getAvgDaysToHire() != null)
                .mapToDouble(row -> row.getAvgDaysToHire() * row.getHireCount())
                .sum();
        return scaled(weightedSum / totalHires);
    }

    /**
     * A percentage, or {@code null} when there is nothing to divide by.
     *
     * <p>Returning {@code null} rather than {@code 0} is deliberate: a source
     * with no applications has an unknown hire rate, not a hire rate of zero,
     * and the dashboard prints a dash for it.</p>
     *
     * @param numerator   the part
     * @param denominator the whole
     * @return the ratio in percent to one decimal, or {@code null}
     */
    public static BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * @param value a duration in days, possibly {@code null}
     * @return the same value rounded to one decimal, or {@code null}
     */
    public static BigDecimal scaled(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
