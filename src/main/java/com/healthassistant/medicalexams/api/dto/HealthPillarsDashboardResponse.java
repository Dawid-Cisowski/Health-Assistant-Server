package com.healthassistant.medicalexams.api.dto;

import java.util.List;

public record HealthPillarsDashboardResponse(
        String overallAiInsight,
        List<HealthPillarSummaryResponse> pillars
) {}
