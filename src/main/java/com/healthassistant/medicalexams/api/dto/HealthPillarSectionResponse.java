package com.healthassistant.medicalexams.api.dto;

import java.util.List;

public record HealthPillarSectionResponse(
        String sectionCode,
        String sectionNamePl,
        Integer score,
        List<HealthPillarMarkerResult> markers
) {}
