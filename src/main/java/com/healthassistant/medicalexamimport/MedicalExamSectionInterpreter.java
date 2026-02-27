package com.healthassistant.medicalexamimport;

import java.util.List;

interface MedicalExamSectionInterpreter {

    /**
     * Generates a plain-language clinical interpretation for each exam section.
     * The returned list has the same size as the input and preserves order.
     * Individual entries may be null when interpretation is unavailable.
     */
    List<String> interpretSections(List<ExtractedExamData.ExtractedSectionData> sections);
}
