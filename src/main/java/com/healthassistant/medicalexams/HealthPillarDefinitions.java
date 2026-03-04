package com.healthassistant.medicalexams;

import java.util.List;
import java.util.Optional;

final class HealthPillarDefinitions {

    private HealthPillarDefinitions() {}

    record SectionDefinition(String code, String namePl, List<String> markerCodes) {}

    record PillarDefinition(String code, String namePl, int ttlMonths, String heroMarkerCode,
                             List<SectionDefinition> sections) {}

    static final List<PillarDefinition> PILLARS = List.of(

            new PillarDefinition("CIRCULATORY", "Układ Krążeniowy", 12, "LDL", List.of(
                    new SectionDefinition("LIPID_PROFILE", "Profil lipidowy",
                            List.of("CHOL", "LDL", "HDL", "TG", "NON_HDL", "LPA", "APO_B", "APO_A1")),
                    new SectionDefinition("HEART_HEALTH", "Biomarkery sercowe",
                            List.of("NT_PRO_BNP", "BNP", "TROPONIN_I", "TROPONIN_T", "CK_MB", "HOMOCYSTEINE",
                                    "ECHO_OVERALL")),
                    new SectionDefinition("COAGULATION", "Układ krzepnięcia",
                            List.of("INR", "PT", "PT_PERCENT", "APTT", "FIBRINOGEN", "D_DIMER")),
                    new SectionDefinition("ECG_SMARTWATCH", "EKG z zegarka",
                            List.of("ECG_RHYTHM", "ECG_AVG_HR", "ECG_DURATION_SEC", "ECG_GAIN", "ECG_PAPER_SPEED")),
                    new SectionDefinition("CHEST_IMAGING", "RTG klatki piersiowej",
                            List.of("CHEST_XRAY_OVERALL"))
            )),

            new PillarDefinition("DIGESTIVE", "Układ Pokarmowy i Nerkowy", 12, "ALT", List.of(
                    new SectionDefinition("LIVER", "Wątroba",
                            List.of("ALT", "AST", "GGT", "BILIR", "ALP", "ALBUMIN")),
                    new SectionDefinition("KIDNEY_URINARY", "Nerki i mocz",
                            List.of("CREAT", "UREA", "UA", "EGFR",
                                    "URINE_COLOR", "URINE_CLARITY", "URINE_PH", "URINE_SG",
                                    "URINE_PROTEIN", "URINE_GLUCOSE", "URINE_KETONES", "URINE_NITRITES",
                                    "URINE_RBC", "URINE_WBC", "URINE_WBC_SEDIMENT", "URINE_BACTERIA",
                                    "URINE_BILIRUBIN", "URINE_UROBILINOGEN", "URINE_CASTS",
                                    "URINE_EPITHELIAL", "URINE_EPITHELIAL_ROUND", "URINE_MUCUS")),
                    new SectionDefinition("PANCREAS", "Trzustka",
                            List.of("AMYLASE", "LIPASE")),
                    new SectionDefinition("GUT", "Przewód pokarmowy",
                            List.of("OCCULT_BLOOD", "CALPROTECTIN", "GIARDIA", "H2_START", "H2_DELTA",
                                    "CH4_MAX", "H2_CH4_DELTA",
                                    "GASTROSCOPY_OVERALL", "COLONOSCOPY_OVERALL",
                                    "USG_ABDOMINAL_OVERALL", "HISTOPATH_OVERALL"))
            )),

            new PillarDefinition("METABOLISM", "Metabolizm i Hormony", 12, "GLU", List.of(
                    new SectionDefinition("GLUCOSE_METABOLISM", "Metabolizm glukozy",
                            List.of("GLU", "HBA1C", "HBA1C_IFCC", "INSULIN", "INSULIN_FASTING")),
                    new SectionDefinition("THYROID", "Tarczyca",
                            List.of("TSH", "FT3", "FT4", "ANTI_TPO", "ANTI_TG", "USG_THYROID_OVERALL")),
                    new SectionDefinition("SEX_HORMONES", "Hormony płciowe",
                            List.of("TESTOSTERONE", "ESTRADIOL", "PROGESTERONE", "FSH", "LH", "SHBG")),
                    new SectionDefinition("STRESS_ADRENAL", "Kortyzol i DHEA-S",
                            List.of("PROLACTIN", "CORTISOL", "DHEAS"))
            )),

            new PillarDefinition("BLOOD_IMMUNITY", "Krew i Odporność", 6, "HGB", List.of(
                    new SectionDefinition("CBC", "Morfologia",
                            List.of("WBC", "RBC", "HGB", "HCT", "MCV", "MCH", "MCHC", "PLT",
                                    "NEUT", "LYMPH", "MONO", "EOS", "BASO",
                                    "NEUT_ABS", "LYMPH_ABS", "MONO_ABS", "EOS_ABS", "BASO_ABS",
                                    "RDW_SD", "RDW_CV", "PDW", "MPV", "PLCR", "PCT",
                                    "NRBC", "NRBC_PERC", "IG_PERC", "IG_ABS")),
                    new SectionDefinition("IRON_METABOLISM", "Gospodarka żelazem",
                            List.of("FERR", "FE", "TIBC")),
                    new SectionDefinition("INFLAMMATION_MARKERS", "Markery zapalne",
                            List.of("CRP", "ESR")),
                    new SectionDefinition("IMMUNITY", "Immunoglobuliny",
                            List.of("IGA", "IGG", "IGM", "IGE_TOTAL", "IGE_SPECIFIC", "ASO", "ANTY_TGT_IGA"))
            )),

            new PillarDefinition("VITAMINS_MINERALS", "Witaminy i Minerały", 12, "VIT_D", List.of(
                    new SectionDefinition("VITAMINS", "Witaminy",
                            List.of("VIT_D", "VIT_B12", "FOLIC_ACID")),
                    new SectionDefinition("ELECTROLYTES", "Elektrolity i minerały",
                            List.of("MG", "K", "NA", "CA", "P", "CL", "ZINC", "COPPER"))
            ))
    );

    static List<String> allMarkerCodes() {
        return PILLARS.stream()
                .flatMap(p -> p.sections().stream())
                .flatMap(s -> s.markerCodes().stream())
                .distinct()
                .toList();
    }

    static List<String> pillarMarkerCodes(PillarDefinition pillar) {
        return pillar.sections().stream()
                .flatMap(s -> s.markerCodes().stream())
                .distinct()
                .toList();
    }

    static Optional<PillarDefinition> findPillar(String code) {
        return PILLARS.stream()
                .filter(p -> p.code().equals(code))
                .findFirst();
    }
}
