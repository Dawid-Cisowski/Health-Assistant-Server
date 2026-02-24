package com.healthassistant.medicalexams.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record UpdateExaminationRequest(
        String title,
        LocalDate date,
        Instant performedAt,
        Instant resultsReceivedAt,
        String laboratory,
        String orderingDoctor,
        String notes,
        String summary,
        String reportText,
        String conclusions,
        String recommendations
) {
}
