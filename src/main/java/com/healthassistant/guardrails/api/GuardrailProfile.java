package com.healthassistant.guardrails.api;

/**
 * Profiles defining different guardrail configurations per use-case.
 */
public enum GuardrailProfile {

    /**
     * Chat assistant - full validation with text length limits and injection detection.
     */
    CHAT(4000, true, true, true),

    /**
     * Image import (meals, workouts, sleep) - no text length limit, focus on injection detection.
     */
    IMAGE_IMPORT(2000, true, true, false),

    /**
     * Data extraction for AI summaries - moderate limits, sanitization focus.
     */
    DATA_EXTRACTION(100, true, false, true);

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

    /**
     * If true, block the request when injection is detected.
     * If false, sanitize the input and allow it to proceed.
     */
    public boolean blockOnDetection() {
        return blockOnDetection;
    }
}
