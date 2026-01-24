package com.healthassistant.guardrails;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralized prompt injection patterns - single source of truth for all guardrails.
 */
final class PromptInjectionPatterns {

    private PromptInjectionPatterns() {
    }

    /**
     * Patterns detecting prompt injection attempts in text input.
     */
    static final List<Pattern> TEXT_INJECTION_PATTERNS = List.of(
            // Instruction override attempts
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions?"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?previous"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|prior)"),

            // System prompt extraction attempts
            Pattern.compile("(?i)system\\s*prompt"),
            Pattern.compile("(?i)show\\s+(me\\s+)?(your|the)\\s+instructions?"),
            Pattern.compile("(?i)what\\s+are\\s+your\\s+instructions?"),
            Pattern.compile("(?i)reveal\\s+(your\\s+)?instructions?"),

            // Role manipulation attempts
            Pattern.compile("(?i)you\\s+are\\s+now"),
            Pattern.compile("(?i)pretend\\s+(to\\s+be|you\\s+are)"),
            Pattern.compile("(?i)act\\s+as\\s+(if|a)"),
            Pattern.compile("(?i)roleplay\\s+as"),

            // Known jailbreak patterns
            Pattern.compile("(?i)\\bDAN\\b"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)developer\\s+mode"),
            Pattern.compile("(?i)bypass\\s+(your\\s+)?restrictions?"),

            // Special token injection
            Pattern.compile("(?i)<\\|system\\|>"),
            Pattern.compile("(?i)<\\|user\\|>"),
            Pattern.compile("(?i)<\\|assistant\\|>"),
            Pattern.compile("(?i)\\[INST\\]"),
            Pattern.compile("(?i)\\[/INST\\]")
    );

    /**
     * Patterns detecting JSON injection attempts (fake tool results).
     */
    static final List<Pattern> JSON_INJECTION_PATTERNS = List.of(
            // Meal extraction fake results
            Pattern.compile("\\{\\s*\"isMeal\"\\s*:"),
            Pattern.compile("\\{\\s*\"caloriesKcal\"\\s*:"),
            Pattern.compile("\\{\\s*\"proteinGrams\"\\s*:"),

            // Sleep extraction fake results
            Pattern.compile("\\{\\s*\"isSleepScreenshot\"\\s*:"),
            Pattern.compile("\\{\\s*\"totalSleepMinutes\"\\s*:"),

            // Workout extraction fake results
            Pattern.compile("\\{\\s*\"isWorkoutScreenshot\"\\s*:"),
            Pattern.compile("\\{\\s*\"exercises\"\\s*:\\s*\\["),

            // Generic tool result patterns
            Pattern.compile("\\{\\s*\"result\"\\s*:"),
            Pattern.compile("\\{\\s*\"data\"\\s*:"),
            Pattern.compile("\\{\\s*\"response\"\\s*:")
    );

    /**
     * Pattern for excessive newlines that might be used to hide injected content.
     */
    static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("[\\r\\n]{3,}");

    /**
     * Pattern for control characters (except tab).
     */
    static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\t]]");

    /**
     * Placeholder used when sanitizing detected injection patterns.
     */
    static final String FILTERED_PLACEHOLDER = "[filtered]";

    /**
     * Placeholder used when sanitizing detected JSON injection.
     */
    static final String FILTERED_JSON_PLACEHOLDER = "[filtered-json]";
}
