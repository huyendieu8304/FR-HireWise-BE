package com.hirewise.be.ai;

/**
 * Computes UC-21's Match Score/Matched-Missing Skills/summary for one
 * candidate CV against one Job's JD. Sits behind an interface (rather than
 * {@code service.AiScreeningService} calling the Anthropic SDK directly) so
 * the underlying AI provider can be swapped later without touching the
 * queueing/persistence logic - see {@link AnthropicMatchingEngine} for the
 * only implementation today.
 */
public interface MatchingEngine {

    /** @return the model identifier this engine calls, stored for audit (BR-AI-02) */
    String modelName();

    /** @return version of the analysis prompt/schema this engine sends - bump when it changes materially (BR-AI-02) */
    String promptVersion();

    /**
     * Analyzes a candidate's CV against a Job's JD.
     *
     * @param jdText     the Job's description + requirements, as plain text
     * @param cvPdfBytes raw bytes of the candidate's CV - must be a PDF (UC-21
     *                   scope note: {@code .doc}/{@code .docx} CVs are not
     *                   sent here, see {@code service.AiScreeningService})
     * @return the structured analysis result
     * @throws MatchingEngineException if the AI Engine call fails or times out (EX-01)
     */
    MatchAnalysisResult analyze(String jdText, byte[] cvPdfBytes);
}
