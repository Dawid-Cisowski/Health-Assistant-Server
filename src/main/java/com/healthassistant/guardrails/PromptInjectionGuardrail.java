package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

import static com.healthassistant.guardrails.PromptInjectionPatterns.*;

/**
 * Detects prompt injection attempts using pattern matching.
 */
@Component
@Slf4j
class PromptInjectionGuardrail implements Guardrail {

    private static final int ORDER = 10;

    @Override
    public GuardrailResult evaluate(String input, GuardrailProfile profile) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.allowed();
        }

        // Check for prompt injection patterns
        if (profile.checkPromptInjection()) {
            Optional<String> detected = detectPattern(input, TEXT_INJECTION_PATTERNS);
            if (detected.isPresent()) {
                log.warn("Prompt injection detected: pattern='{}', input_preview='{}'",
                        detected.get(), sanitizeForLog(input));

                if (profile.blockOnDetection()) {
                    return GuardrailResult.blocked(
                            "I can only help with health-related questions.",
                            "Prompt injection detected: " + detected.get()
                    );
                }
                // If not blocking, sanitization will happen later
            }
        }

        // Check for JSON injection patterns
        if (profile.checkJsonInjection()) {
            Optional<String> detected = detectPattern(input, JSON_INJECTION_PATTERNS);
            if (detected.isPresent()) {
                log.warn("JSON injection detected: pattern='{}', input_preview='{}'",
                        detected.get(), sanitizeForLog(input));

                if (profile.blockOnDetection()) {
                    return GuardrailResult.blocked(
                            "Invalid input format.",
                            "JSON injection detected: " + detected.get()
                    );
                }
            }
        }

        return GuardrailResult.allowed();
    }

    private Optional<String> detectPattern(String input, java.util.List<Pattern> patterns) {
        return patterns.stream()
                .filter(pattern -> pattern.matcher(input).find())
                .map(Pattern::pattern)
                .findFirst();
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        String sanitized = input.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 100));
    }

    @Override
    public int order() {
        return ORDER;
    }
}
