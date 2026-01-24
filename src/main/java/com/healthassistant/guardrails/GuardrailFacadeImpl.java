package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailBlockedException;
import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class GuardrailFacadeImpl implements GuardrailFacade {

    private final GuardrailChain guardrailChain;
    private final InputSanitizer inputSanitizer;

    @Override
    public GuardrailResult validateText(String input, GuardrailProfile profile) {
        log.debug("Validating text with profile {}: {} chars",
                profile, input != null ? input.length() : 0);
        return guardrailChain.evaluate(input, profile);
    }

    @Override
    public String validateAndSanitize(String input, GuardrailProfile profile) {
        GuardrailResult result = validateText(input, profile);

        if (result.blocked()) {
            throw new GuardrailBlockedException(result.userMessage(), result.internalReason());
        }

        return result.sanitizedInput() != null ? result.sanitizedInput() : input;
    }

    @Override
    public String sanitizeOnly(String input, GuardrailProfile profile) {
        if (input == null) {
            return "";
        }
        return inputSanitizer.sanitize(input, profile);
    }
}
