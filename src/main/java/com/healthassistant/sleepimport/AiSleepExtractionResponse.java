package com.healthassistant.sleepimport;

record AiSleepExtractionResponse(
        boolean isSleepScreenshot,
        double confidence,
        String sleepDate,
        String sleepStart,
        String wakeTime,
        Integer totalSleepMinutes,
        Integer sleepScore,
        AiPhases phases,
        String qualityLabel,
        String validationError
) {
    record AiPhases(
            Integer lightSleepMinutes,
            Integer deepSleepMinutes,
            Integer remSleepMinutes,
            Integer awakeMinutes
    ) {}
}
