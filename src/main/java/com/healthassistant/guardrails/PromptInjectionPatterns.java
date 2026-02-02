package com.healthassistant.guardrails;

import java.util.List;
import java.util.regex.Pattern;

final class PromptInjectionPatterns {

    private PromptInjectionPatterns() {
    }

    static final List<Pattern> TEXT_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions?"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?previous"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|prior)"),
            Pattern.compile("(?i)system\\s*prompt"),
            Pattern.compile("(?i)show\\s+(me\\s+)?(your|the)\\s+instructions?"),
            Pattern.compile("(?i)what\\s+are\\s+your\\s+instructions?"),
            Pattern.compile("(?i)reveal\\s+(your\\s+)?instructions?"),
            Pattern.compile("(?i)you\\s+are\\s+now"),
            Pattern.compile("(?i)pretend\\s+(to\\s+be|you\\s+are)"),
            Pattern.compile("(?i)act\\s+as\\s+(if|a)"),
            Pattern.compile("(?i)roleplay\\s+as"),
            Pattern.compile("(?i)\\bDAN\\b"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)developer\\s+mode"),
            Pattern.compile("(?i)bypass\\s+(your\\s+)?restrictions?"),
            Pattern.compile("(?i)<\\|system\\|>"),
            Pattern.compile("(?i)<\\|user\\|>"),
            Pattern.compile("(?i)<\\|assistant\\|>"),
            Pattern.compile("(?i)\\[INST\\]"),
            Pattern.compile("(?i)\\[/INST\\]")
    );

    static final List<Pattern> JSON_INJECTION_PATTERNS = List.of(
            Pattern.compile("\\{\\s*\"isMeal\"\\s*:"),
            Pattern.compile("\\{\\s*\"caloriesKcal\"\\s*:"),
            Pattern.compile("\\{\\s*\"proteinGrams\"\\s*:"),
            Pattern.compile("\\{\\s*\"isSleepScreenshot\"\\s*:"),
            Pattern.compile("\\{\\s*\"totalSleepMinutes\"\\s*:"),
            Pattern.compile("\\{\\s*\"isWorkoutScreenshot\"\\s*:"),
            Pattern.compile("\\{\\s*\"exercises\"\\s*:\\s*\\["),
            Pattern.compile("\\{\\s*\"result\"\\s*:"),
            Pattern.compile("\\{\\s*\"data\"\\s*:"),
            Pattern.compile("\\{\\s*\"response\"\\s*:")
    );

    static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("[\\r\\n]{3,}");

    static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\t\n\r]]");

    static final String FILTERED_PLACEHOLDER = "[filtered]";

    static final String FILTERED_JSON_PLACEHOLDER = "[filtered-json]";
}
