package com.healthassistant.medicalexams.api.dto;

import java.util.List;

public record ExamTypeDefinitionResponse(
        String code,
        String namePl,
        String nameEn,
        String displayType,
        List<String> specialties,
        String category,
        int sortOrder
) {
}
