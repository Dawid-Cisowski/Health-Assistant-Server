package com.healthassistant.medicalexamimport.api.dto;

import java.util.List;

public record DraftSectionResponse(
        String examTypeCode,
        String title,
        String reportText,
        String conclusions,
        List<ExtractedResultData> results
) {}
