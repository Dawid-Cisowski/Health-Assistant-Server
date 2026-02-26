package com.healthassistant.medicalexamimport.api.dto;

import java.util.List;

public record DraftSectionUpdateRequest(
        String examTypeCode,
        String title,
        String reportText,
        String conclusions,
        List<ExtractedResultData> results
) {}
