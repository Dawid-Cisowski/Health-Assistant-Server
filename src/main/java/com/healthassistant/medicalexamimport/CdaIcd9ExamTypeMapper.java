package com.healthassistant.medicalexamimport;

import java.util.Map;

final class CdaIcd9ExamTypeMapper {

    private CdaIcd9ExamTypeMapper() {}

    static final Map<String, String> ICD9_TO_EXAM_TYPE = Map.ofEntries(
            Map.entry("C55", "MORPHOLOGY"),
            Map.entry("A01", "URINE"),
            Map.entry("A11", "URINE"),
            Map.entry("A19", "URINE"),
            Map.entry("G21", "COAGULATION"),
            Map.entry("O77", "ELECTROLYTES"),
            Map.entry("M87", "ELECTROLYTES"),
            Map.entry("M88", "ELECTROLYTES"),
            Map.entry("O80", "ELECTROLYTES"),
            Map.entry("M37", "KIDNEY_PANEL"),
            Map.entry("M45", "KIDNEY_PANEL"),
            Map.entry("L43", "GLUCOSE"),
            Map.entry("L55", "GLUCOSE"),
            Map.entry("I09", "LIVER_PANEL"),
            Map.entry("L31", "LIVER_PANEL"),
            Map.entry("M71", "LIPID_PANEL"),
            Map.entry("I67", "LIPID_PANEL"),
            Map.entry("A17", "STOOL_TEST"),
            Map.entry("X13", "STOOL_TEST"),
            Map.entry("O83", "VITAMINS"),
            Map.entry("O91", "VITAMINS"),
            Map.entry("N45", "THYROID"),
            Map.entry("N46", "THYROID"),
            Map.entry("L69", "THYROID"),
            Map.entry("O55", "THYROID"),
            Map.entry("O69", "THYROID"),
            Map.entry("O09", "THYROID"),
            Map.entry("M09", "INFLAMMATION"),
            Map.entry("I81", "INFLAMMATION"),
            Map.entry("L05", "INFLAMMATION"),
            Map.entry("N24", "CARDIAC_BIOMARKERS"),
            Map.entry("O41", "HORMONES"),
            Map.entry("M69", "LIPID_PANEL"),
            Map.entry("99.999", "LIVER_PANEL")
    );

    static String resolve(String icd9Code) {
        return ICD9_TO_EXAM_TYPE.getOrDefault(icd9Code, "OTHER");
    }
}
