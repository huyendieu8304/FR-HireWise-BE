package com.hirewise.be.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "engine", havingValue = "anthropic", matchIfMissing = true)
    public AnthropicClient anthropicClient(@Value("${app.ai.anthropic.api-key}") String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
