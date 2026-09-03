package com.hirewise.be.domain;

/**
 * Lifecycle of one {@link AiScreeningRun} (UC-21).
 * <ul>
 *   <li>{@code PENDING} - queued by {@code service.AiScreeningService} right
 *       after an Application is created/re-submitted (or AF-01 manual
 *       re-run); not yet picked up by {@code event.AiScreeningDispatcher}.</li>
 *   <li>{@code SUCCEEDED} - the Claude API call returned a result;
 *       {@link AiScreeningRun#getMatchScore()}/{@link AiScreeningRun#getSummary()}
 *       and its {@link AiSkillMatch} rows are populated.</li>
 *   <li>{@code FAILED} - the AI Engine call errored or timed out (EX-01);
 *       {@link AiScreeningRun#getErrorMessage()} carries why. The Recruiter
 *       still sees "Không thể phân tích" and processes the Application
 *       manually - AI failure never blocks the recruitment flow (BR-AI-01).</li>
 * </ul>
 */
public enum AiScreeningStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
