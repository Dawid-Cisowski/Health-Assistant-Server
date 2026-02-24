package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LabResultResponse(
        UUID id,
        String markerCode,
        String markerName,
        String category,
        BigDecimal valueNumeric,
        String unit,
        BigDecimal originalValueNumeric,
        String originalUnit,
        boolean conversionApplied,
        BigDecimal refRangeLow,
        BigDecimal refRangeHigh,
        String refRangeText,
        BigDecimal defaultRefRangeLow,
        BigDecimal defaultRefRangeHigh,
        String valueText,
        String flag,
        int sortOrder
) {
}
