package com.healthassistant.guardrails;

import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;

interface Guardrail {

    GuardrailResult evaluate(String input, GuardrailProfile profile);

    int order();
}
