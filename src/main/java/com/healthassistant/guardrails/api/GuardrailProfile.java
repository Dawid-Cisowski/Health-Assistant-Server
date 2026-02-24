package com.healthassistant.guardrails.api;

public enum GuardrailProfile {

    CHAT(4000, true, true, true),

    IMAGE_IMPORT(2000, true, true, false),

    DATA_EXTRACTION(100, true, false, true),

    MEDICAL_EXAM_IMPORT(5000, true, true, false);

    private final int maxTextLength;
    private final boolean checkPromptInjection;
    private final boolean checkJsonInjection;
    private final boolean blockOnDetection;

    GuardrailProfile(int maxTextLength, boolean checkPromptInjection,
                     boolean checkJsonInjection, boolean blockOnDetection) {
        this.maxTextLength = maxTextLength;
        this.checkPromptInjection = checkPromptInjection;
        this.checkJsonInjection = checkJsonInjection;
        this.blockOnDetection = blockOnDetection;
    }

    public int maxTextLength() {
        return maxTextLength;
    }

    public boolean checkPromptInjection() {
        return checkPromptInjection;
    }

    public boolean checkJsonInjection() {
        return checkJsonInjection;
    }

    public boolean blockOnDetection() {
        return blockOnDetection;
    }
}
