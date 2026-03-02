package com.healthassistant.medicalexams.api.dto;

import java.time.LocalDate;

public record HealthPillarSummaryResponse(
        String pillarCode,
        String pillarNamePl,
        Integer score,
        boolean isOutdated,
        LocalDate latestDataDate,
        HealthPillarHeroMetric heroMetric,
        String aiInsight
) {}
