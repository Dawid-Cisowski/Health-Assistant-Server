package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
class GuardrailChain {

    private final List<Guardrail> guardrails;
    private final InputSanitizer inputSanitizer;

    GuardrailChain(List<Guardrail> guardrails, InputSanitizer inputSanitizer) {
        this.guardrails = guardrails.stream()
                .sorted(Comparator.comparingInt(Guardrail::order))
                .toList();
        this.inputSanitizer = inputSanitizer;

        log.info("Initialized guardrail chain with {} guardrails: {}",
                this.guardrails.size(),
                this.guardrails.stream().map(g -> g.getClass().getSimpleName()).toList());
    }

    GuardrailResult evaluate(String input, GuardrailProfile profile) {
        return guardrails.stream()
                .map(guardrail -> {
                    GuardrailResult result = guardrail.evaluate(input, profile);
                    if (result.blocked()) {
                        log.debug("Input blocked by {}: {}",
                                guardrail.getClass().getSimpleName(),
                                result.internalReason());
                    }
                    return result;
                })
                .filter(GuardrailResult::blocked)
                .findFirst()
                .orElseGet(() -> {
                    String sanitized = inputSanitizer.sanitize(input, profile);
                    return GuardrailResult.allowed(sanitized);
                });
    }
}
