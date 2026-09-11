package com.hirewise.be.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * UC-21: exposes the Anthropic Claude API client used by
 * {@code ai.AnthropicMatchingEngine} to compute AI Match Score/skill
 * breakdown. {@code app.ai.anthropic.api-key} is HireWise's OWN Anthropic
 * account key (set via {@code .env.local}/{@code .env.prod}) - unrelated to
 * any credential on the machine this backend happens to run on.
 * <p>
 * Only created when {@code app.ai.engine=anthropic} (the default) - when
 * {@code app.ai.engine=mock} (see {@code ai.MockMatchingEngine}), this bean
 * (and any API key/network call) is skipped entirely, so local dev/testing
 * never requires real Anthropic credit.
 */
@Configuration
public class AnthropicConfig {

    /**
     * The SDK's own default timeout is generous (built for long streaming
     * completions) - way more than a small {@code maxTokens(2048)}
     * structured-JSON call over 1 CV should ever need. Left uncapped, a slow/
     * stalled connection to Anthropic blocks {@code event.AiScreeningDispatcher}'s
     * single scheduler thread for that entire duration - since that thread
     * processes every {@code PENDING} run ONE AT A TIME (see its class
     * Javadoc), one hung call stalls the whole AI Screening queue for every
     * Recruiter, not just the run that triggered it. Failing fast here means
     * a bad call becomes 1 FAILED run within ~1 minute (EX-01, still just an
     * AF-01 "Phân tích lại" retry away) instead of an indefinite company-wide
     * stall on a support-only feature (BR-AI-01).
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "engine", havingValue = "anthropic", matchIfMissing = true)
    public AnthropicClient anthropicClient(@Value("${app.ai.anthropic.api-key}") String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(REQUEST_TIMEOUT)
                .build();
    }
}
