package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;

public record MarkerDefinitionResponse(
        String code,
        String namePl,
        String nameEn,
        String category,
        String specialty,
        String standardUnit,
        BigDecimal refRangeLowDefault,
        BigDecimal refRangeWarningHighDefault,
        BigDecimal refRangeHighDefault,
        String description,
        int sortOrder
) {
}
