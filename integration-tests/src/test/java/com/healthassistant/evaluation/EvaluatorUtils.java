package com.healthassistant.evaluation;

import java.util.Arrays;

/**
 * Utility class for LLM-as-a-Judge evaluators.
 * Provides common parsing logic for evaluating AI responses.
 */
final class EvaluatorUtils {

    private static final String[] RESPONSE_PREFIXES = {
        "RESPONSE:", "ANSWER:", "OUTPUT:", "RESULT:", "EVALUATION:"
    };

    private EvaluatorUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Parse YES/NO from LLM judge response, handling various response formats.
     * The judge may prefix the response with "RESPONSE:", "ANSWER:", etc.
     *
     * @param response the raw response from the LLM judge
     * @return true if the response indicates YES, false otherwise
     */
    static boolean parseYesNoResponse(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalized = response.trim().toUpperCase();

        // Direct YES at start
        if (normalized.startsWith("YES")) {
            return true;
        }

        // Handle common prefixes: "RESPONSE: YES", "ANSWER: YES", etc.
        boolean matchesPrefix = Arrays.stream(RESPONSE_PREFIXES)
            .filter(normalized::startsWith)
            .map(prefix -> normalized.substring(prefix.length()).trim())
            .anyMatch(afterPrefix -> afterPrefix.startsWith("YES"));

        if (matchesPrefix) {
            return true;
        }

        // Check if YES appears in the first line (before any period or newline)
        String firstPart = normalized.split("[.\\n]")[0].trim();
        return firstPart.contains("YES") && !firstPart.contains("NO");
    }
}
