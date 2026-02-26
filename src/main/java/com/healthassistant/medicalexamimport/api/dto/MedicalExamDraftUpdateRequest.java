package com.healthassistant.medicalexamimport.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MedicalExamDraftUpdateRequest(
        LocalDate date,
        Instant performedAt,
        String laboratory,
        String orderingDoctor,
        List<DraftSectionUpdateRequest> sections
) {}
