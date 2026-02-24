package com.healthassistant.medicalexamimport;

import java.math.BigDecimal;
import java.util.List;

record AiMedicalExamExtractionResponse(
        boolean isMedicalReport,
        double confidence,
        String examTypeCode,
        String title,
        String performedAt,
        String laboratory,
        String orderingDoctor,
        String reportText,
        String conclusions,
        String validationError,
        List<AiExtractedResult> results
) {
    record AiExtractedResult(
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
}
