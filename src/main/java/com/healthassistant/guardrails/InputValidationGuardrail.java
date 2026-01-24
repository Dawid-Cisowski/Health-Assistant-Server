package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import org.springframework.stereotype.Component;

/**
 * Validates basic input constraints: null, blank, length.
 */
@Component
class InputValidationGuardrail implements Guardrail {

    private static final int ORDER = 0;

    @Override
    public GuardrailResult evaluate(String input, GuardrailProfile profile) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.blocked(
                    "Message cannot be empty",
                    "Input was null or blank"
            );
        }

        if (profile.maxTextLength() > 0 && input.length() > profile.maxTextLength()) {
            // For blocking profiles, reject immediately
            if (profile.blockOnDetection()) {
                return GuardrailResult.blocked(
                        "Message is too long",
                        "Input length " + input.length() + " exceeds max " + profile.maxTextLength()
                );
            }
            // For sanitizing profiles, truncation will happen in InputSanitizer
        }

        return GuardrailResult.allowed();
    }

    @Override
    public int order() {
        return ORDER;
    }
}
