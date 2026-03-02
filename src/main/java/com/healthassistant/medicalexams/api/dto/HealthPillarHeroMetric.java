package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HealthPillarHeroMetric(
        String markerCode,
        String markerNamePl,
        BigDecimal valueNumeric,
        String unit,
        String flag,
        LocalDate date
) {}
