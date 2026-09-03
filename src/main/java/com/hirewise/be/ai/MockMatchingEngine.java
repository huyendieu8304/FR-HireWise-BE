package com.hirewise.be.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link MatchingEngine} that never calls any network/API - returns a
 * plausible-looking but entirely fake result instantly. Exists so UC-21's
 * full flow (enqueue → dispatch → persist → Kanban Badge → Applicant Card)
 * can be exercised end-to-end during local dev/testing without spending
 * real Anthropic API credit (a Claude Pro/Max subscription does NOT cover
 * API usage - see {@code AnthropicMatchingEngine}, a separate, billed
 * account).
 * <p>
 * Active only when {@code app.ai.engine=mock} (`.env.local`'s
 * {@code HIREWISE_AI_ENGINE=mock}) - switch back to {@code anthropic} once
 * real AI judgment is actually needed (demo, real usage). Every summary
 * this returns is prefixed {@code "[MOCK]"} so nobody mistakes a fake
 * result for a real one.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "engine", havingValue = "mock")
public class MockMatchingEngine implements MatchingEngine {

    private static final List<String> SAMPLE_SKILLS =
            List.of("Java", "Spring Boot", "SQL", "Docker", "Kubernetes", "REST API", "Git", "CI/CD");

    @Override
    public String modelName() {
        return "mock-engine";
    }

    @Override
    public String promptVersion() {
        return "mock";
    }

    @Override
    public MatchAnalysisResult analyze(String jdText, byte[] cvPdfBytes) {
        log.info("MockMatchingEngine: trả kết quả giả lập, KHÔNG gọi Anthropic API thật (app.ai.engine=mock)");

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int matchScore = random.nextInt(40, 96);
        List<String> shuffled = new java.util.ArrayList<>(SAMPLE_SKILLS);
        java.util.Collections.shuffle(shuffled, random);
        int splitAt = random.nextInt(2, shuffled.size() - 1);

        return new MatchAnalysisResult(
                matchScore,
                "[MOCK] Đây là kết quả giả lập để test giao diện - không phải phân tích AI thật. "
                        + "Đổi app.ai.engine=anthropic trong .env.local để dùng Claude API thật.",
                shuffled.subList(0, splitAt),
                shuffled.subList(splitAt, shuffled.size()));
    }
}
