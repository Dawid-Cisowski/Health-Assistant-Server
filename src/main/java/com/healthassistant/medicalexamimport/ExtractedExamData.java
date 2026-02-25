package com.healthassistant.medicalexamimport;

import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record ExtractedExamData(
        boolean valid,
        String examTypeCode,
        String title,
        LocalDate date,
        Instant performedAt,
        String laboratory,
        String orderingDoctor,
        String reportText,
        String conclusions,
        List<ExtractedResultData> results,
        BigDecimal confidence,
        Long promptTokens,
        Long completionTokens,
        String errorMessage
) {
    static ExtractedExamData valid(
            String examTypeCode, String title, LocalDate date, Instant performedAt,
            String laboratory, String orderingDoctor, String reportText,
            String conclusions, List<ExtractedResultData> results,
            BigDecimal confidence, Long promptTokens, Long completionTokens) {
        return new ExtractedExamData(
                true, examTypeCode, title, date, performedAt, laboratory,
                orderingDoctor, reportText, conclusions, results,
                confidence, promptTokens, completionTokens, null);
    }

    static ExtractedExamData invalid(String errorMessage, BigDecimal confidence) {
        return new ExtractedExamData(
                false, null, null, null, null, null, null, null, null,
                List.of(), confidence, null, null, errorMessage);
    }
}
