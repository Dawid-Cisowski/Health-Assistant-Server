package com.healthassistant.medicalexamimport;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class CdaMarkerNameMapper {

    private CdaMarkerNameMapper() {}

    private static final Map<String, String> GENERIC_MAP = Map.ofEntries(
            // Morphology — red blood cell indices
            Map.entry("erytrocyty", "RBC"),
            Map.entry("hemoglobina", "HGB"),
            Map.entry("hematokryt", "HCT"),
            Map.entry("mcv", "MCV"),
            Map.entry("mch", "MCH"),
            Map.entry("mchc", "MCHC"),
            Map.entry("płytki krwi", "PLT"),
            Map.entry("rdw-sd", "RDW_SD"),
            Map.entry("rdw-cv", "RDW_CV"),
            Map.entry("pdw", "PDW"),
            Map.entry("mpv", "MPV"),
            Map.entry("p-lcr", "PLCR"),
            Map.entry("pct", "PCT"),
            // Morphology — white blood cells
            Map.entry("neutrofile", "NEUT"),
            Map.entry("limfocyty", "LYMPH"),
            Map.entry("monocyty", "MONO"),
            Map.entry("eozynofile", "EOS"),
            Map.entry("bazofile", "BASO"),
            Map.entry("leukocyty", "WBC"),
            // Coagulation
            Map.entry("inr", "INR"),
            Map.entry("wskaźnik protrombiny (quicka)", "PT_PERCENT"),
            Map.entry("czas protrombinowy", "PT"),
            Map.entry("aptt", "APTT"),
            Map.entry("fibrynogen", "FIBRINOGEN"),
            Map.entry("d-dimer", "D_DIMER"),
            // Electrolytes
            Map.entry("wapń całkowity", "CA"),
            Map.entry("magnez", "MG"),
            Map.entry("sód", "NA"),
            Map.entry("potas", "K"),
            Map.entry("chlorki", "CL"),
            Map.entry("fosfor", "P"),
            // Kidney
            Map.entry("kreatynina", "CREAT"),
            Map.entry("egfr", "EGFR"),
            Map.entry("kwas moczowy", "UA"),
            Map.entry("urea", "UREA"),
            // Glucose
            Map.entry("glukoza", "GLU"),
            // Liver
            Map.entry("albumina", "ALBUMIN"),
            Map.entry("alt", "ALT"),
            Map.entry("alat", "ALT"),
            Map.entry("ast", "AST"),
            Map.entry("aspat", "AST"),
            Map.entry("ggtp", "GGT"),
            Map.entry("ggt", "GGT"),
            Map.entry("bilirubina", "BILIR"),
            // Lipids
            Map.entry("cholesterol całkowity", "CHOL"),
            Map.entry("cholesterol nie-hdl", "NON_HDL"),
            Map.entry("cholesterol hdl", "HDL"),
            Map.entry("cholesterol ldl", "LDL"),
            Map.entry("triglicerydy", "TG"),
            Map.entry("trójglicerydy", "TG"),
            Map.entry("apo b", "APO_B"),
            Map.entry("apo a1", "APO_A1"),
            // Stool
            Map.entry("krew utajona w kale", "OCCULT_BLOOD"),
            Map.entry("krew utajona w kale (bez diety)", "OCCULT_BLOOD"),
            Map.entry("giardia lamblia met. elisa", "GIARDIA"),
            // Vitamins / minerals
            Map.entry("witamina b12", "VIT_B12"),
            Map.entry("witamina d metabolit 25(oh)", "VIT_D"),
            Map.entry("żelazo", "FE"),
            // Thyroid
            Map.entry("tsh", "TSH"),
            Map.entry("ft3", "FT3"),
            Map.entry("ft4", "FT4"),
            // Inflammation
            Map.entry("crp", "CRP"),
            Map.entry("opad opadanie krwinek", "ESR"),
            Map.entry("ferrytyna", "FERR"),
            // Cardiac biomarkers
            Map.entry("nt pro-bnp", "NT_PRO_BNP"),
            Map.entry("bnp", "BNP"),
            Map.entry("troponina i", "TROPONIN_I"),
            Map.entry("troponina t", "TROPONIN_T"),
            Map.entry("ck-mb", "CK_MB"),
            // Thyroid autoimmune
            Map.entry("anty-tpo", "ANTI_TPO"),
            Map.entry("anty-tg", "ANTI_TG"),
            // Hormones
            Map.entry("testosteron", "TESTOSTERONE"),
            Map.entry("estradiol", "ESTRADIOL"),
            Map.entry("progesteron", "PROGESTERONE"),
            Map.entry("fsh", "FSH"),
            Map.entry("lh", "LH"),
            Map.entry("prolaktyna", "PROLACTIN"),
            Map.entry("kortyzol", "CORTISOL"),
            Map.entry("dhea-s", "DHEAS"),
            Map.entry("shbg", "SHBG"),
            // Advanced lipids
            Map.entry("lipoproteina(a) [lp(a)]", "LPA")
    );

    static Optional<String> resolve(String displayName, String examTypeCode) {
        String normalized = normalize(displayName);

        if ("URINE".equals(examTypeCode)) {
            Optional<String> urineCode = resolveUrine(normalized);
            if (urineCode.isPresent()) {
                return urineCode;
            }
        }

        // Special case: HBA1C variants
        if (normalized.contains("hemoglobina glikowana") || normalized.contains("hba1c") || normalized.contains("glikowana")) {
            return Optional.of(normalized.contains("ifcc") ? "HBA1C_IFCC" : "HBA1C");
        }

        return Optional.ofNullable(GENERIC_MAP.get(normalized));
    }

    private static Optional<String> resolveUrine(String normalized) {
        return switch (normalized) {
            case "erytrocyty" -> Optional.of("URINE_RBC");
            case "leukocyty" -> Optional.of("URINE_WBC");
            case "nabłonki płaskie" -> Optional.of("URINE_EPITHELIAL");
            case "bakterie" -> Optional.of("URINE_BACTERIA");
            case "wałeczki szkliste" -> Optional.of("URINE_CASTS");
            case "glukoza" -> Optional.of("URINE_GLUCOSE");
            case "ketony" -> Optional.of("URINE_KETONES");
            case "białko" -> Optional.of("URINE_PROTEIN");
            case "urobilinogen" -> Optional.of("URINE_UROBILINOGEN");
            case "bilirubina" -> Optional.of("URINE_BILIRUBIN");
            case "ph" -> Optional.of("URINE_PH");
            case "azotyny" -> Optional.of("URINE_NITRITES");
            default -> Optional.empty();
        };
    }

    static String normalize(String name) {
        if (name == null) return "";
        // Strip "(ICD-9: XXX)" suffix (case-insensitive match)
        String stripped = name.replaceAll("(?i)\\s*\\(ICD-9:[^)]*\\)\\s*", "").trim();
        return stripped.toLowerCase(Locale.ROOT).trim();
    }
}
