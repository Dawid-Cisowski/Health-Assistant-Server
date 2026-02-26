package com.healthassistant.medicalexamimport.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicalExamDraftResponse(
        UUID draftId,
        LocalDate date,
        Instant performedAt,
        String laboratory,
        String orderingDoctor,
        List<DraftSectionResponse> sections,
        BigDecimal confidence,
        String status,
        Instant expiresAt,
        String errorMessage
) {
    public static MedicalExamDraftResponse success(
            UUID draftId, LocalDate date, Instant performedAt,
            String laboratory, String orderingDoctor, List<DraftSectionResponse> sections,
            BigDecimal confidence, String status, Instant expiresAt) {
        return new MedicalExamDraftResponse(
                draftId, date, performedAt, laboratory, orderingDoctor,
                sections, confidence, status, expiresAt, null
        );
    }

    public static MedicalExamDraftResponse failure(String errorMessage) {
        return new MedicalExamDraftResponse(
                null, null, null, null, null,
                List.of(), BigDecimal.ZERO, "FAILED", null, errorMessage
        );
    }
}
