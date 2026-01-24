package com.healthassistant.guardrails.api;

/**
 * Exception thrown when guardrail blocks an input.
 */
public class GuardrailBlockedException extends RuntimeException {

    private final String userMessage;

    public GuardrailBlockedException(String userMessage, String internalReason) {
        super(internalReason);
        this.userMessage = userMessage;
    }

    /**
     * Safe message to show to the user.
     */
    public String getUserMessage() {
        return userMessage;
    }
}
