package com.healthassistant.medicalexams.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record CreateExaminationRequest(
        @NotBlank String examTypeCode,
        @NotBlank String title,
        @NotNull LocalDate date,
        Instant performedAt,
        Instant resultsReceivedAt,
        String laboratory,
        String orderingDoctor,
        String notes,
        String reportText,
        String conclusions,
        String recommendations,
        String source
) {
}
