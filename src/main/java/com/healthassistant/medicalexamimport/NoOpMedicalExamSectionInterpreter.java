package com.healthassistant.medicalexamimport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnMissingBean(MedicalExamSectionInterpreter.class)
class NoOpMedicalExamSectionInterpreter implements MedicalExamSectionInterpreter {

    @Override
    public List<String> interpretSections(List<ExtractedExamData.ExtractedSectionData> sections) {
        return Collections.nCopies(sections.size(), null);
    }
}
