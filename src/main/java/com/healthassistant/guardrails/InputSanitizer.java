package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.healthassistant.guardrails.PromptInjectionPatterns.*;

@Component
class InputSanitizer {

    String sanitize(String input, GuardrailProfile profile) {
        if (input == null) {
            return null;
        }

        String result = input;

        result = CONTROL_CHARS.matcher(result).replaceAll("");
        result = EXCESSIVE_NEWLINES.matcher(result).replaceAll("\n\n");

        if (profile.checkPromptInjection()) {
            result = sanitizePatterns(result, TEXT_INJECTION_PATTERNS, FILTERED_PLACEHOLDER);
        }

        if (profile.checkJsonInjection()) {
            result = sanitizePatterns(result, JSON_INJECTION_PATTERNS, FILTERED_JSON_PLACEHOLDER);
        }

        if (profile.maxTextLength() > 0 && result.length() > profile.maxTextLength()) {
            result = result.substring(0, profile.maxTextLength());
        }

        return result.trim();
    }

    private String sanitizePatterns(String input, java.util.List<Pattern> patterns, String replacement) {
        return patterns.stream()
                .reduce(input,
                        (result, pattern) -> pattern.matcher(result).replaceAll(replacement),
                        (a, b) -> b);
    }
}
