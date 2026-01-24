package com.healthassistant.guardrails.api;

/**
 * Result of a guardrail evaluation.
 *
 * @param blocked        whether the input was blocked
 * @param userMessage    safe message to show to user (if blocked)
 * @param internalReason detailed reason for logging (never expose to user)
 * @param sanitizedInput sanitized version of input (if applicable)
 */
public record GuardrailResult(
        boolean blocked,
        String userMessage,
        String internalReason,
        String sanitizedInput
) {
    public static GuardrailResult allowed() {
        return new GuardrailResult(false, null, null, null);
    }

    public static GuardrailResult allowed(String sanitizedInput) {
        return new GuardrailResult(false, null, null, sanitizedInput);
    }

    public static GuardrailResult blocked(String userMessage, String internalReason) {
        return new GuardrailResult(true, userMessage, internalReason, null);
    }

    public boolean isAllowed() {
        return !blocked;
    }
}
