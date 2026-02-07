package com.healthassistant.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Central service for recording custom AI metrics.
 * All AI services inject this instead of MeterRegistry directly.
 */
@Component
public class AiMetricsRecorder {

    private final MeterRegistry registry;

    public AiMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    // --- Chat ---

    public void recordChatRequest(Timer.Sample sample, String status) {
        sample.stop(Timer.builder("ai.chat.duration")
                .tag("status", status)
                .register(registry));
        registry.counter("ai.chat.requests", "status", status).increment();
    }

    public void recordChatTokens(Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            registry.summary("ai.chat.tokens.prompt").record(promptTokens);
            registry.counter("ai.tokens.cost.total", "feature", "chat",
                    "token_type", "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            registry.summary("ai.chat.tokens.completion").record(completionTokens);
            registry.counter("ai.tokens.cost.total", "feature", "chat",
                    "token_type", "completion").increment(completionTokens);
        }
    }

    public void recordToolCall(String toolName, Timer.Sample sample, String status) {
        sample.stop(Timer.builder("ai.chat.tool_calls.duration")
                .tag("tool", toolName)
                .tag("status", status)
                .register(registry));
        registry.counter("ai.chat.tool_calls", "tool", toolName).increment();
    }

    public void recordRateLimitRejected() {
        registry.counter("ai.chat.rate_limit.rejected").increment();
    }

    // --- Summary / Report ---

    public void recordSummaryRequest(String type, Timer.Sample sample, String status, boolean cacheHit) {
        sample.stop(Timer.builder("ai.summary.duration")
                .tag("type", type)
                .register(registry));
        registry.counter("ai.summary.requests",
                "type", type, "status", status,
                "cache", cacheHit ? "hit" : "miss").increment();
    }

    public void recordSummaryTokens(String type, Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            registry.summary("ai.summary.tokens.prompt", "type", type).record(promptTokens);
            registry.counter("ai.tokens.cost.total", "feature", type,
                    "token_type", "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            registry.summary("ai.summary.tokens.completion", "type", type).record(completionTokens);
            registry.counter("ai.tokens.cost.total", "feature", type,
                    "token_type", "completion").increment(completionTokens);
        }
    }

    public void recordSummaryInputTruncated() {
        registry.counter("ai.summary.input.truncated").increment();
    }

    // --- Import ---

    public void recordImportRequest(String importType, Timer.Sample sample, String status, String mode) {
        sample.stop(Timer.builder("ai.import.duration")
                .tag("import_type", importType)
                .register(registry));
        registry.counter("ai.import.requests",
                "import_type", importType, "status", status,
                "mode", mode).increment();
    }

    public void recordImportTokens(String importType, Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            registry.summary("ai.import.tokens.prompt", "import_type", importType).record(promptTokens);
            registry.counter("ai.tokens.cost.total", "feature", "import_" + importType,
                    "token_type", "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            registry.summary("ai.import.tokens.completion", "import_type", importType).record(completionTokens);
            registry.counter("ai.tokens.cost.total", "feature", "import_" + importType,
                    "token_type", "completion").increment(completionTokens);
        }
    }

    public void recordImportConfidence(String importType, double confidence) {
        registry.summary("ai.import.confidence", "import_type", importType).record(confidence);
    }

    public void recordImportImageCount(String importType, int imageCount) {
        registry.summary("ai.import.images_per_request", "import_type", importType).record(imageCount);
    }

    public void recordDraftAction(String action) {
        registry.counter("ai.import.draft.confirmed", "action", action).increment();
    }

    public void recordReanalysis(String status) {
        registry.counter("ai.import.reanalysis", "status", status).increment();
    }

    // --- Guardrails ---

    public void recordGuardrailCheck(String profile, String result) {
        registry.counter("ai.guardrail.checks", "profile", profile, "result", result).increment();
    }

    public void recordInjectionDetected(String profile, String patternType) {
        registry.counter("ai.guardrail.injection.detected",
                "profile", profile, "pattern_type", patternType).increment();
    }

    public void recordGuardrailValidationFailed(String profile, String reason) {
        registry.counter("ai.guardrail.validation.failed",
                "profile", profile, "reason", reason).increment();
    }

    // --- Errors ---

    public void recordAiError(String feature, String errorType) {
        registry.counter("ai.errors", "feature", feature, "error_type", errorType).increment();
    }
}
