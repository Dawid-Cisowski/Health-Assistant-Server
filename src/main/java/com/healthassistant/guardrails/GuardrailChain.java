package com.healthassistant.guardrails;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
class GuardrailChain {

    private final List<Guardrail> guardrails;
    private final InputSanitizer inputSanitizer;
    private final AiMetricsRecorder aiMetrics;

    GuardrailChain(List<Guardrail> guardrails, InputSanitizer inputSanitizer, AiMetricsRecorder aiMetrics) {
        this.guardrails = guardrails.stream()
                .sorted(Comparator.comparingInt(Guardrail::order))
                .toList();
        this.inputSanitizer = inputSanitizer;
        this.aiMetrics = aiMetrics;

        log.info("Initialized guardrail chain with {} guardrails: {}",
                this.guardrails.size(),
                this.guardrails.stream().map(g -> g.getClass().getSimpleName()).toList());
    }

    GuardrailResult evaluate(String input, GuardrailProfile profile) {
        GuardrailResult result = guardrails.stream()
                .map(guardrail -> {
                    GuardrailResult r = guardrail.evaluate(input, profile);
                    if (r.blocked()) {
                        log.debug("Input blocked by {}: {}",
                                guardrail.getClass().getSimpleName(),
                                r.internalReason());
                    }
                    return r;
                })
                .filter(GuardrailResult::blocked)
                .findFirst()
                .orElseGet(() -> {
                    String sanitized = inputSanitizer.sanitize(input, profile);
                    return GuardrailResult.allowed(sanitized);
                });

        if (result.blocked()) {
            aiMetrics.recordGuardrailCheck(profile.name(), "blocked");
        } else if (result.sanitizedInput() != null && !input.equals(result.sanitizedInput())) {
            aiMetrics.recordGuardrailCheck(profile.name(), "sanitized");
        } else {
            aiMetrics.recordGuardrailCheck(profile.name(), "passed");
        }

        return result;
    }
}
