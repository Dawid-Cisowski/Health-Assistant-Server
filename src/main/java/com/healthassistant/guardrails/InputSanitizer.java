package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.healthassistant.guardrails.PromptInjectionPatterns.*;

/**
 * Sanitizes user input by removing or replacing potentially dangerous content.
 * Used when profile allows sanitization instead of blocking.
 */
@Component
class InputSanitizer {

    /**
     * Sanitize input text according to profile rules.
     *
     * @param input   the input to sanitize
     * @param profile the guardrail profile
     * @return sanitized input
     */
    String sanitize(String input, GuardrailProfile profile) {
        if (input == null) {
            return null;
        }

        String result = input;

        // Remove control characters
        result = CONTROL_CHARS.matcher(result).replaceAll("");

        // Normalize excessive newlines
        result = EXCESSIVE_NEWLINES.matcher(result).replaceAll("\n\n");

        // Sanitize prompt injection patterns
        if (profile.checkPromptInjection()) {
            result = sanitizePatterns(result, TEXT_INJECTION_PATTERNS, FILTERED_PLACEHOLDER);
        }

        // Sanitize JSON injection patterns
        if (profile.checkJsonInjection()) {
            result = sanitizePatterns(result, JSON_INJECTION_PATTERNS, FILTERED_JSON_PLACEHOLDER);
        }

        // Truncate to max length
        if (profile.maxTextLength() > 0 && result.length() > profile.maxTextLength()) {
            result = result.substring(0, profile.maxTextLength());
        }

        return result.trim();
    }

    private String sanitizePatterns(String input, java.util.List<Pattern> patterns, String replacement) {
        String result = input;
        for (Pattern pattern : patterns) {
            result = pattern.matcher(result).replaceAll(replacement);
        }
        return result;
    }
}
