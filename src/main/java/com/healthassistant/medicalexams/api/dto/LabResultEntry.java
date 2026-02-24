package com.healthassistant.medicalexams.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record LabResultEntry(
        @NotBlank String markerCode,
        @NotBlank String markerName,
        String category,
        BigDecimal valueNumeric,
        String unit,
        BigDecimal refRangeLow,
        BigDecimal refRangeHigh,
        String refRangeText,
        String valueText,
        int sortOrder
) {
}
