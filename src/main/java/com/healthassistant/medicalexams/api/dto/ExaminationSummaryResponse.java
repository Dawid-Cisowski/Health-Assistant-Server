package com.healthassistant.medicalexams.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExaminationSummaryResponse(
        UUID id,
        String examTypeCode,
        String title,
        LocalDate date,
        String status,
        String displayType,
        List<String> specialties,
        String laboratory,
        int resultCount,
        int abnormalCount,
        boolean hasPrimaryAttachment,
        String primaryAttachmentUrl,
        Instant createdAt
) {
}
