package com.healthassistant.guardrails.api;

/**
 * Public facade for guardrail operations.
 * Entry point for all modules needing input validation and sanitization.
 */
public interface GuardrailFacade {

    /**
     * Validate and potentially sanitize text input.
     *
     * @param input   the input to validate
     * @param profile the guardrail profile defining rules
     * @return result with blocked status or sanitized input
     */
    GuardrailResult validateText(String input, GuardrailProfile profile);

    /**
     * Convenience method: validate and return sanitized text or throw if blocked.
     *
     * @param input   the input to validate
     * @param profile the guardrail profile
     * @return sanitized input
     * @throws GuardrailBlockedException if input is blocked
     */
    String validateAndSanitize(String input, GuardrailProfile profile);

    /**
     * Sanitize text without blocking (always returns sanitized version).
     * Use when you want to clean input but not reject it.
     *
     * @param input   the input to sanitize
     * @param profile the guardrail profile
     * @return sanitized input (never null, may be empty)
     */
    String sanitizeOnly(String input, GuardrailProfile profile);
}
