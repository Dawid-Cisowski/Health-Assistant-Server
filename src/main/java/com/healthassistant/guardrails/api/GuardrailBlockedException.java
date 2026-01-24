package com.healthassistant.guardrails.api;

public class GuardrailBlockedException extends RuntimeException {

    private final String userMessage;

    public GuardrailBlockedException(String userMessage, String internalReason) {
        super(internalReason);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
