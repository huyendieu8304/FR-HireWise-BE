package com.hirewise.be.repository;

import com.hirewise.be.repository.projection.SourceRoiRow;
import com.hirewise.be.repository.projection.StageVelocityRow;
import com.hirewise.be.repository.projection.TimeToHireRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-42/UC-43: kiem cac phep tong hop chay tren Postgres that.
 *
 * <p>Day la slice test dau tien cua project, va no can thiet vi 2 query cua
 * UC-43 dung window function ({@code LEAD}) cung ordered-set aggregate
 * ({@code PERCENTILE_CONT}) - khong mock nao kiem duoc chung tinh dung hay
 * khong, va chinh phan "suy ra thoi gian o Stage tu 2 event lien tiep" la cho
 * de sai nhat trong ca 2 use case.</p>
 *
 * <p>Schema do chinh Flyway dung len, nen test cung bao ve luon cac migration:
 * doi ten cot hay doi seed ma quen sua query thi test do ngay.</p>
 *
 * <p>Du lieu nen lay tu V25 (Default Hiring Pipeline: NEW - SCREENING -
 * INTERVIEW - OFFER - HIRED - REJECTED, va Job "Senior Backend Engineer"), test
 * chi them candidate/application/stage history.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportRepositoryTest {

    private static final UUID JOB_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant APPLIED_AT = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant FROM = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-20T00:00:00Z");
    private static final List<UUID> ALL_JOBS = List.of(new UUID(0L, 0L));

    /**
     * Started once for the whole class and left to Ryuk to reap. Testcontainers
     * 2.x in this project has no JUnit 5 extension on the classpath, so the
     * lifecycle is managed here rather than by {@code @Testcontainers}.
     */
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void cleanApplications() {
        jdbcTemplate.update("DELETE FROM application_stage_history");
        jdbcTemplate.update("DELETE FROM applications");
    }

    @Test
    void sourceRoi_groupsByUtmSourceAndCountsHires() {
        UUID fromLinkedin = application("linkedin", APPLIED_AT);
        UUID direct = application(null, APPLIED_AT);

        enter(fromLinkedin, "NEW", APPLIED_AT);
        enter(fromLinkedin, "HIRED", APPLIED_AT.plusSeconds(10 * 86400));
        enter(direct, "NEW", APPLIED_AT);

        List<SourceRoiRow> rows = reportRepository.aggregateSourceRoi(
                FROM, TO, true, ALL_JOBS, null, null);

        assertThat(rows).hasSize(2);
        SourceRoiRow linkedin = rowFor(rows, "linkedin");
        assertThat(linkedin.getApplicationCount()).isEqualTo(1);
        assertThat(linkedin.getHireCount()).isEqualTo(1);
        assertThat(linkedin.getAvgDaysToHire()).isEqualTo(10.0);

        // A NULL source is reported under the empty key, never folded into a
        // channel - direct traffic is not LinkedIn traffic.
        SourceRoiRow directRow = rowFor(rows, "");
        assertThat(directRow.getApplicationCount()).isEqualTo(1);
        assertThat(directRow.getHireCount()).isZero();
        assertThat(directRow.getAvgDaysToHire()).isNull();
    }

    @Test
    void sourceRoi_ignoresApplicationsOutsideTheDateRange() {
        enter(application("linkedin", Instant.parse("2026-01-15T00:00:00Z")), "NEW", FROM);

        List<SourceRoiRow> rows = reportRepository.aggregateSourceRoi(
                FROM, TO, true, ALL_JOBS, null, null);

        assertThat(rows).isEmpty();
    }

    @Test
    void sourceRoi_ignoresJobsOutsideTheCallerScope() {
        enter(application("linkedin", APPLIED_AT), "NEW", APPLIED_AT);

        List<SourceRoiRow> rows = reportRepository.aggregateSourceRoi(
                FROM, TO, false, List.of(UUID.randomUUID()), null, null);

        assertThat(rows).isEmpty();
    }

    @Test
    void velocity_measuresTheGapBetweenConsecutiveEvents() {
        UUID application = application("linkedin", APPLIED_AT);
        enter(application, "NEW", APPLIED_AT);
        enter(application, "SCREENING", APPLIED_AT.plusSeconds(2 * 86400));
        enter(application, "INTERVIEW", APPLIED_AT.plusSeconds(5 * 86400));

        List<StageVelocityRow> rows = velocity();

        StageVelocityRow newStage = stageFor(rows, "NEW");
        assertThat(newStage.getAvgDays()).isEqualTo(2.0);
        assertThat(newStage.getCompletedCount()).isEqualTo(1);
        assertThat(newStage.getAdvancedCount()).isEqualTo(1);
        assertThat(newStage.getWaitingCount()).isZero();

        StageVelocityRow screening = stageFor(rows, "SCREENING");
        assertThat(screening.getAvgDays()).isEqualTo(3.0);
    }

    @Test
    void velocity_leavesTheOpenVisitOutOfTheAverageButCountsItAsWaiting() {
        UUID application = application("linkedin", APPLIED_AT);
        enter(application, "NEW", APPLIED_AT);
        enter(application, "SCREENING", APPLIED_AT.plusSeconds(2 * 86400));

        List<StageVelocityRow> rows = velocity();

        // Screening has been running since 3 June and it is now 20 June, so 17
        // days and nowhere near finished. Folding that into the mean as if it
        // were a completed 17-day visit would be a guess; leaving it out
        // entirely would hide the stage that is stuck.
        StageVelocityRow screening = stageFor(rows, "SCREENING");
        assertThat(screening.getCompletedCount()).isZero();
        assertThat(screening.getAvgDays()).isNull();
        assertThat(screening.getWaitingCount()).isEqualTo(1);
        assertThat(screening.getMaxWaitingDays()).isEqualTo(17.0);
    }

    @Test
    void velocity_countsARollbackAsTwoSeparateVisits() {
        UUID application = application("linkedin", APPLIED_AT);
        enter(application, "NEW", APPLIED_AT);
        enter(application, "SCREENING", APPLIED_AT.plusSeconds(86400));
        rollback(application, "NEW", APPLIED_AT.plusSeconds(3 * 86400));
        enter(application, "SCREENING", APPLIED_AT.plusSeconds(6 * 86400));
        enter(application, "INTERVIEW", APPLIED_AT.plusSeconds(7 * 86400));

        List<StageVelocityRow> rows = velocity();

        // The application sat in New twice, for 1 day and then 3 days. Both are
        // real time the stage consumed, so both count and the mean is 2 days.
        StageVelocityRow newStage = stageFor(rows, "NEW");
        assertThat(newStage.getCompletedCount()).isEqualTo(2);
        assertThat(newStage.getAvgDays()).isEqualTo(2.0);
    }

    @Test
    void velocity_separatesAdvancedFromRejected() {
        UUID advanced = application("linkedin", APPLIED_AT);
        enter(advanced, "SCREENING", APPLIED_AT);
        enter(advanced, "INTERVIEW", APPLIED_AT.plusSeconds(86400));

        UUID rejected = application("facebook", APPLIED_AT);
        enter(rejected, "SCREENING", APPLIED_AT);
        enter(rejected, "REJECTED", APPLIED_AT.plusSeconds(3 * 86400));

        List<StageVelocityRow> rows = velocity();

        StageVelocityRow screening = stageFor(rows, "SCREENING");
        assertThat(screening.getEnteredCount()).isEqualTo(2);
        assertThat(screening.getAdvancedCount()).isEqualTo(1);
        assertThat(screening.getRejectedCount()).isEqualTo(1);
    }

    @Test
    void velocity_excludesTerminalStages() {
        UUID application = application("linkedin", APPLIED_AT);
        enter(application, "NEW", APPLIED_AT);
        enter(application, "HIRED", APPLIED_AT.plusSeconds(86400));
        UUID other = application("facebook", APPLIED_AT);
        enter(other, "NEW", APPLIED_AT);
        enter(other, "REJECTED", APPLIED_AT.plusSeconds(86400));

        List<StageVelocityRow> rows = velocity();

        assertThat(rows).extracting(StageVelocityRow::getStageCode)
                .containsExactly("NEW")
                .doesNotContain("HIRED", "REJECTED");
    }

    @Test
    void velocity_returnsStagesInPipelineOrder() {
        UUID application = application("linkedin", APPLIED_AT);
        enter(application, "OFFER", APPLIED_AT);
        enter(application, "NEW", APPLIED_AT.plusSeconds(86400));
        enter(application, "INTERVIEW", APPLIED_AT.plusSeconds(2 * 86400));

        List<StageVelocityRow> rows = velocity();

        assertThat(rows).extracting(StageVelocityRow::getStageCode)
                .containsExactly("NEW", "INTERVIEW", "OFFER");
    }

    @Test
    void timeToHire_averagesFromApplyToTheFirstSuccessEvent() {
        UUID hired = application("linkedin", APPLIED_AT);
        enter(hired, "NEW", APPLIED_AT);
        enter(hired, "HIRED", APPLIED_AT.plusSeconds(20 * 86400));

        UUID stillRunning = application("facebook", APPLIED_AT);
        enter(stillRunning, "NEW", APPLIED_AT);

        TimeToHireRow row = reportRepository.aggregateTimeToHire(FROM, TO, true, ALL_JOBS, null, null);

        assertThat(row.getHiredCount()).isEqualTo(1);
        assertThat(row.getAvgDaysToHire()).isEqualTo(20.0);
    }

    @Test
    void timeToHire_noHire_returnsZeroCountAndNullAverage() {
        enter(application("linkedin", APPLIED_AT), "NEW", APPLIED_AT);

        TimeToHireRow row = reportRepository.aggregateTimeToHire(FROM, TO, true, ALL_JOBS, null, null);

        assertThat(row.getHiredCount()).isZero();
        assertThat(row.getAvgDaysToHire()).isNull();
    }

    private List<StageVelocityRow> velocity() {
        return reportRepository.aggregateStageVelocity(FROM, TO, NOW, true, ALL_JOBS, null, null);
    }

    private static SourceRoiRow rowFor(List<SourceRoiRow> rows, String sourceKey) {
        return rows.stream()
                .filter(row -> sourceKey.equals(row.getSourceKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong thay dong nguon: " + sourceKey));
    }

    private static StageVelocityRow stageFor(List<StageVelocityRow> rows, String stageCode) {
        return rows.stream()
                .filter(row -> stageCode.equals(row.getStageCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong thay Stage: " + stageCode));
    }

    /**
     * Inserts a candidate and their application on the seeded Job.
     *
     * @param source    the {@code utm_source} to record, or {@code null} for direct traffic
     * @param appliedAt when the application arrived
     * @return the new application id
     */
    private UUID application(String source, Instant appliedAt) {
        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO candidates (id, full_name, primary_email, phone)
                VALUES (?, 'Ung vien test', ?, '0900000000')
                """, candidateId, candidateId + "@test.local");
        jdbcTemplate.update("""
                INSERT INTO applications (id, candidate_id, job_position_id, current_stage_id,
                                          status, applied_at, source)
                VALUES (?, ?, ?, ?, 'NEW', ?, ?)
                """, applicationId, candidateId, JOB_ID, stageId("NEW"),
                java.sql.Timestamp.from(appliedAt), source);
        return applicationId;
    }

    private void enter(UUID applicationId, String stageCode, Instant changedAt) {
        history(applicationId, stageCode, changedAt, "MANUAL");
    }

    private void rollback(UUID applicationId, String stageCode, Instant changedAt) {
        history(applicationId, stageCode, changedAt, "ROLLBACK");
    }

    private void history(UUID applicationId, String stageCode, Instant changedAt, String transitionType) {
        jdbcTemplate.update("""
                INSERT INTO application_stage_history (application_id, to_stage_id, transition_type, changed_at)
                VALUES (?, ?, ?, ?)
                """, applicationId, stageId(stageCode), transitionType,
                java.sql.Timestamp.from(changedAt));
    }

    private Long stageId(String code) {
        return jdbcTemplate.queryForObject("""
                SELECT ps.pipeline_stage_id FROM pipeline_stages ps
                JOIN pipeline_templates pt ON pt.pipeline_template_id = ps.pipeline_template_id
                WHERE pt.name = 'Default Hiring Pipeline' AND ps.code = ?
                """, Long.class, code);
    }
}
