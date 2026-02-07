package com.healthassistant.guardrails;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InputValidationGuardrail implements Guardrail {

    private static final int ORDER = 0;
    private final AiMetricsRecorder aiMetrics;

    @Override
    public GuardrailResult evaluate(String input, GuardrailProfile profile) {
        if (input == null || input.isBlank()) {
            aiMetrics.recordGuardrailValidationFailed(profile.name(), "blank");
            return GuardrailResult.blocked(
                    "Message cannot be empty",
                    "Input was null or blank"
            );
        }

        if (profile.maxTextLength() > 0
                && input.length() > profile.maxTextLength()
                && profile.blockOnDetection()) {
            aiMetrics.recordGuardrailValidationFailed(profile.name(), "too_long");
            return GuardrailResult.blocked(
                    "Message is too long",
                    "Input length " + input.length() + " exceeds max " + profile.maxTextLength()
            );
        }

        return GuardrailResult.allowed();
    }

    @Override
    public int order() {
        return ORDER;
    }
}
