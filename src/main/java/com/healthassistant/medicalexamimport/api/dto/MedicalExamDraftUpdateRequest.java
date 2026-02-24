package com.healthassistant.medicalexamimport.api.dto;

import java.time.Instant;
import java.util.List;

public record MedicalExamDraftUpdateRequest(
        String examTypeCode,
        String title,
        Instant performedAt,
        String laboratory,
        String orderingDoctor,
        String reportText,
        String conclusions,
        List<ExtractedResultData> results
) {}
