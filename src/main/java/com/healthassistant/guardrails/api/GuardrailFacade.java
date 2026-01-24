package com.healthassistant.guardrails.api;

public interface GuardrailFacade {

    GuardrailResult validateText(String input, GuardrailProfile profile);

    String validateAndSanitize(String input, GuardrailProfile profile);

    String sanitizeOnly(String input, GuardrailProfile profile);
}
