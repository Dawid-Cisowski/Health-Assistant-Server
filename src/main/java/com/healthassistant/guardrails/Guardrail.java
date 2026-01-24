package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;

/**
 * Interface for individual guardrail implementations.
 */
interface Guardrail {

    /**
     * Evaluate the input against this guardrail.
     *
     * @param input   the input to evaluate
     * @param profile the guardrail profile defining rules
     * @return result indicating if input is allowed or blocked
     */
    GuardrailResult evaluate(String input, GuardrailProfile profile);

    /**
     * Order in the guardrail chain (lower = earlier).
     */
    int order();
}
