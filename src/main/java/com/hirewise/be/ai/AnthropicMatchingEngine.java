package com.hirewise.be.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * {@link MatchingEngine} backed by a real call to the Anthropic Claude API
 * (UC-21). Sends the candidate's CV as a native PDF document content block
 * (no separate text-extraction step/dependency needed) plus the Job's JD as
 * plain text, and asks for a structured JSON result via
 * {@code outputConfig(Class)} so the response is parsed straight into
 * {@link MatchAnalysisSchema} - no manual JSON parsing/tool-call round-trip.
 * <p>
 * Active only when {@code app.ai.engine=anthropic} (the default) - swap to
 * {@code app.ai.engine=mock} ({@link MockMatchingEngine}) for local dev/
 * testing that never spends real Anthropic API credit.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "engine", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicMatchingEngine implements MatchingEngine {

    /** BR-AI-02: bump whenever the analysis prompt/schema below changes materially. */
    static final String PROMPT_VERSION = "v1";

    private static final String INSTRUCTIONS_TEMPLATE = """
            Bạn là trợ lý sàng lọc hồ sơ ứng tuyển cho hệ thống ATS HireWise (UC-21).
            Dưới đây là Job Description và file CV (đính kèm PDF) của 1 ứng viên.
            So khớp kỹ năng/kinh nghiệm thể hiện trong CV với yêu cầu trong JD, rồi trả về đúng \
            các trường đã yêu cầu: matchScore (0-100), summary (2-3 câu tiếng Việt), \
            matchedSkills, missingSkills. Chỉ dựa vào đúng nội dung JD và CV được cung cấp, \
            không suy đoán hay bịa thêm thông tin không có trong 2 tài liệu này.

            Job Description:
            %s
            """;

    private final AnthropicClient client;
    private final String modelId;

    public AnthropicMatchingEngine(AnthropicClient client,
                                    @Value("${app.ai.anthropic.model:claude-haiku-4-5}") String modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    @Override
    public String modelName() {
        return modelId;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @Override
    public MatchAnalysisResult analyze(String jdText, byte[] cvPdfBytes) {
        try {
            DocumentBlockParam cvDocument = DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder()
                            .data(Base64.getEncoder().encodeToString(cvPdfBytes))
                            .build())
                    .title("CV ứng viên")
                    .build();

            String instructions = INSTRUCTIONS_TEMPLATE.formatted(jdText == null ? "" : jdText);

            StructuredMessageCreateParams<MatchAnalysisSchema> params = MessageCreateParams.builder()
                    .model(modelId)
                    .maxTokens(2048L)
                    .outputConfig(MatchAnalysisSchema.class)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofDocument(cvDocument),
                            ContentBlockParam.ofText(TextBlockParam.builder().text(instructions).build())))
                    .build();

            MatchAnalysisSchema result = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .orElseThrow(() -> new MatchingEngineException("Claude không trả về nội dung phân tích nào", null))
                    .text();

            return new MatchAnalysisResult(result.matchScore(), result.summary(),
                    result.matchedSkills(), result.missingSkills());
        } catch (MatchingEngineException e) {
            throw e;
        } catch (AnthropicServiceException e) {
            // Anthropic's error body (e.g. "Your credit balance is too low...", invalid
            // API key, rate limit...) is far more readable than the exception's own
            // getMessage(), which dumps the raw "{status}: {json body}" string - extract
            // just the human message so Recruiter never sees a raw JSON blob (EX-01).
            String friendly = extractErrorMessage(e);
            log.warn("AI Screening call to {} failed ({}): {}", modelId, e.statusCode(), friendly);
            throw new MatchingEngineException(friendly, e);
        } catch (Exception e) {
            log.warn("AI Screening call to {} failed: {}", modelId, e.getMessage());
            throw new MatchingEngineException("Gọi AI Engine thất bại: " + e.getMessage(), e);
        }
    }

    private static String extractErrorMessage(AnthropicServiceException e) {
        try {
            JsonNode body = e.body().convert(JsonNode.class);
            String message = body.path("error").path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // Error body wasn't the expected {error:{message:...}} shape - fall back below.
        }
        return e.getMessage() != null ? e.getMessage() : "Lỗi không xác định từ AI Engine.";
    }

    /** Wire schema Claude is asked to return - see {@link MatchAnalysisResult} for the app-internal shape. */
    record MatchAnalysisSchema(
            @JsonPropertyDescription("Phần trăm phù hợp tổng thể giữa CV và JD, từ 0 đến 100")
            int matchScore,
            @JsonPropertyDescription("2-3 câu tiếng Việt tóm tắt điểm mạnh/điểm yếu của ứng viên so với JD")
            String summary,
            @JsonPropertyDescription("Danh sách kỹ năng JD yêu cầu mà CV thể hiện có")
            List<String> matchedSkills,
            @JsonPropertyDescription("Danh sách kỹ năng JD yêu cầu mà CV chưa thể hiện")
            List<String> missingSkills) {
    }
}
