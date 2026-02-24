package com.healthassistant.medicalexamimport.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MedicalExamDraftResponse(
        UUID draftId,
        String examTypeCode,
        String title,
        Instant performedAt,
        String laboratory,
        String orderingDoctor,
        String reportText,
        String conclusions,
        List<ExtractedResultData> results,
        BigDecimal confidence,
        String status,
        Instant expiresAt,
        String errorMessage
) {
    public static MedicalExamDraftResponse failure(String errorMessage) {
        return new MedicalExamDraftResponse(
                null, null, null, null, null, null, null, null,
                List.of(), BigDecimal.ZERO, "FAILED", null, errorMessage
        );
    }
}
