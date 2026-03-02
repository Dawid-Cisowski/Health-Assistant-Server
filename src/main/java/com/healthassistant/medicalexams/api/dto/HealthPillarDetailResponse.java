package com.healthassistant.medicalexams.api.dto;

import java.time.LocalDate;
import java.util.List;

public record HealthPillarDetailResponse(
        String pillarCode,
        String pillarNamePl,
        Integer score,
        boolean isOutdated,
        LocalDate latestDataDate,
        HealthPillarHeroMetric heroMetric,
        List<HealthPillarSectionResponse> sections,
        String aiInsight
) {}
