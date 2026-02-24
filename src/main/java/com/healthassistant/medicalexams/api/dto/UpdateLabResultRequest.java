package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;

public record UpdateLabResultRequest(
        BigDecimal valueNumeric,
        String unit,
        BigDecimal refRangeLow,
        BigDecimal refRangeHigh,
        String refRangeText,
        String valueText
) {
}
