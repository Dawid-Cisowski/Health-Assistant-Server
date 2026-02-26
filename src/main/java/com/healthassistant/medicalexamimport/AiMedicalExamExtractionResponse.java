package com.healthassistant.medicalexamimport;

import java.math.BigDecimal;
import java.util.List;

record AiMedicalExamExtractionResponse(
        boolean isMedicalReport,
        double confidence,
        String validationError,
        String date,
        String performedAt,
        String laboratory,
        String orderingDoctor,
        List<AiExamSection> sections
) {
    record AiExamSection(
            String examTypeCode,
            String title,
            String reportText,
            String conclusions,
            List<AiExtractedResult> results
    ) {}

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
