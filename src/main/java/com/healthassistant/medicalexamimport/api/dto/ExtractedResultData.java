package com.healthassistant.medicalexamimport.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedResultData(
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
        String valueText,
        int sortOrder
) {}
