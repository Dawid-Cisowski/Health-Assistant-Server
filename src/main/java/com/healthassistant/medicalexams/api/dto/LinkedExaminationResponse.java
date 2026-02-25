package com.healthassistant.medicalexams.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LinkedExaminationResponse(
        UUID id,
        String examTypeCode,
        String title,
        LocalDate date,
        String status,
        String displayType,
        List<String> specialties
) {
}
