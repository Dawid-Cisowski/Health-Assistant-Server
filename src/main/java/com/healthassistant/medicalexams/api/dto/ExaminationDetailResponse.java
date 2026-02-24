package com.healthassistant.medicalexams.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExaminationDetailResponse(
        UUID id,
        String examTypeCode,
        String title,
        LocalDate date,
        String status,
        String displayType,
        List<String> specialties,
        Instant performedAt,
        Instant resultsReceivedAt,
        String laboratory,
        String orderingDoctor,
        String notes,
        String summary,
        String reportText,
        String conclusions,
        String recommendations,
        String source,
        List<LabResultResponse> results,
        List<ExaminationAttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt
) {
}
