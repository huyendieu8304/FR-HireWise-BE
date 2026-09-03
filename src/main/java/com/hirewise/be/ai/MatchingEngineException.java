package com.hirewise.be.ai;

/**
 * Thrown when the AI Engine call fails or times out (UC-21 EX-01) - caught
 * by {@code event.AiScreeningDispatcher}, which marks the run {@code FAILED}
 * with this exception's message rather than propagating it. Never reaches
 * an HTTP response - the Recruiter simply sees "Không thể phân tích" and
 * keeps working manually (BR-AI-01).
 */
public class MatchingEngineException extends RuntimeException {

    public MatchingEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
