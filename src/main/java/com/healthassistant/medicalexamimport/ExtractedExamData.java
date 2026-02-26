package com.healthassistant.medicalexamimport;

import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record ExtractedExamData(
        boolean valid,
        String errorMessage,
        BigDecimal confidence,
        LocalDate date,
        Instant performedAt,
        String laboratory,
        String orderingDoctor,
        List<ExtractedSectionData> sections
) {
    record ExtractedSectionData(
            String examTypeCode,
            String title,
            String reportText,
            String conclusions,
            List<ExtractedResultData> results
    ) {}

    static ExtractedExamData valid(
            LocalDate date, Instant performedAt, String laboratory, String orderingDoctor,
            List<ExtractedSectionData> sections, BigDecimal confidence) {
        return new ExtractedExamData(
                true, null, confidence,
                date, performedAt, laboratory, orderingDoctor, sections);
    }

    static ExtractedExamData invalid(String errorMessage, BigDecimal confidence) {
        return new ExtractedExamData(
                false, errorMessage, confidence,
                null, null, null, null, List.of());
    }
}
