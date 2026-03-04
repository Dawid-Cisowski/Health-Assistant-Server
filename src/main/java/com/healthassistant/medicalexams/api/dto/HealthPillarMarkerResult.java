package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HealthPillarMarkerResult(
        String markerCode,
        String markerNamePl,
        BigDecimal valueNumeric,
        String unit,
        String valueText,
        String flag,
        Integer score,
        LocalDate date,
        BigDecimal refRangeLow,
        BigDecimal refRangeHigh
) {}
