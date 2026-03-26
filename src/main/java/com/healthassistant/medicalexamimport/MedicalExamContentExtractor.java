package com.healthassistant.medicalexamimport;

import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;
import com.healthassistant.medicalexams.api.ExamSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class MedicalExamContentExtractor {

    private static final int MAX_RESULTS_PER_SECTION = 100;
    private static final String MIME_JPEG = "image/jpeg";

    private final ChatClient chatClient;
    private final GuardrailFacade guardrailFacade;

    ExtractedExamData extract(String description, List<MultipartFile> files) {
        log.info("Extracting medical exam data. files: {}, description: {}",
                files != null ? files.size() : 0,
                description != null ? description.length() + " chars" : "none");

        try {
            var response = callAI(description, files);
            return transformToExtractedData(response);
        } catch (MedicalExamExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI extraction failed: {}", e.getMessage(), e);
            throw new MedicalExamExtractionException("Failed to extract medical exam data: " + e.getMessage(), e);
        }
    }

    private AiMedicalExamExtractionResponse callAI(String description, List<MultipartFile> files) {
        var response = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(userSpec -> {
                    userSpec.text(buildUserPrompt(description, files));
                    if (files != null) {
                        files.forEach(file -> attachFileToSpec(userSpec, file));
                    }
                })
                .call()
                .entity(AiMedicalExamExtractionResponse.class);

        if (response == null) {
            throw new MedicalExamExtractionException("AI returned empty response");
        }
        return response;
    }

    private String buildSystemPrompt() {
        return """
                You are a medical data extraction expert. Your task is to extract structured data
                from medical examination documents, lab reports, imaging reports, and endoscopy reports.

                AVAILABLE EXAM TYPE CODES:
                MORPHOLOGY, LIPID_PANEL, THYROID, GLUCOSE, URINE, ALLERGY_PANEL, HISTOPATHOLOGY,
                LIVER_PANEL, KIDNEY_PANEL, ELECTROLYTES, INFLAMMATION, VITAMINS, HORMONES, COAGULATION,
                CARDIAC_BIOMARKERS, STOOL_TEST, SIBO, SEROLOGY, IMMUNOLOGY,
                ABDOMINAL_USG, THYROID_USG, CHEST_XRAY, ECHO, ECG, GASTROSCOPY, COLONOSCOPY, OTHER

                CARDIAC_BIOMARKERS: NT pro-BNP, BNP, Troponin, CK-MB and other cardiac markers.
                STOOL_TEST: Fecal occult blood (krew utajona w kale), calprotectin, stool culture, parasitology.
                SIBO: Hydrogen/methane breath test (SIBO, IMO, test oddechowy wodorowy/metanowy).
                SEROLOGY: Antibody tests — ASO (antystreptolizyna), anti-tTG IgA (celiakia), ANCA, ANA, etc.
                IMMUNOLOGY: Immunoglobulin quantification — IgA, IgG, IgM levels.

                COMMON MARKER CODES (use these when applicable, for LAB exams only):
                WBC, RBC, HGB, HCT, MCV, MCH, MCHC, PLT (morphology — basic parameters)
                RDW_SD, RDW_CV, PDW, MPV, PLCR, PCT (morphology — platelet/RBC indices)
                IG_PERC, IG_ABS, NRBC, NRBC_PERC (morphology — immature cells)
                NEUT, LYMPH, MONO, EOS, BASO (morphology — WBC PERCENTAGES only, unit must be %)
                NEUT_ABS, LYMPH_ABS, MONO_ABS, EOS_ABS, BASO_ABS (morphology — WBC ABSOLUTE COUNTS, unit tys/µl or G/L)
                CHOL, LDL, HDL, TG, NON_HDL, LPA, APO_B, APO_A1 (lipid panel)
                TSH, FT3, FT4, ANTI_TPO, ANTI_TG (thyroid)
                GLU, HBA1C, HBA1C_IFCC, INSULIN, INSULIN_FASTING (glucose)
                ALT, AST, GGT, BILIR, ALP, ALBUMIN (liver)
                CREAT, UREA, UA, EGFR (kidney)
                CRP, ESR, FERR (inflammation)
                CA, MG, NA, K, P, CL (electrolytes)
                INR, PT, PT_PERCENT, APTT, FIBRINOGEN, D_DIMER (coagulation)
                NT_PRO_BNP, BNP, TROPONIN_I, TROPONIN_T, CK_MB (cardiac biomarkers)
                TESTOSTERONE, ESTRADIOL, PROGESTERONE, FSH, LH, PROLACTIN, CORTISOL, DHEAS, SHBG (hormones)
                VIT_D, VIT_B12, FOLIC_ACID, FE, TIBC (vitamins/minerals)
                IGA, IGG, IGM (immunoglobulins)
                ASO, ANTY_TGT_IGA (serology)
                IGE_TOTAL, IGE_SPECIFIC (allergy)
                URINE_PH, URINE_SG, URINE_COLOR, URINE_CLARITY, URINE_PROTEIN, URINE_GLUCOSE,
                URINE_KETONES, URINE_NITRITES, URINE_RBC, URINE_WBC, URINE_BACTERIA,
                URINE_BILIRUBIN, URINE_UROBILINOGEN, URINE_CASTS, URINE_EPITHELIAL,
                URINE_EPITHELIAL_ROUND, URINE_WBC_SEDIMENT, URINE_MUCUS (urine)
                OCCULT_BLOOD, CALPROTECTIN, GIARDIA (stool)
                H2_START, H2_DELTA, CH4_MAX, H2_CH4_DELTA (SIBO breath test)
                ECG_RHYTHM, ECG_AVG_HR, ECG_DURATION_SEC, ECG_GAIN, ECG_PAPER_SPEED (ECG examination)
                GASTROSCOPY_OVERALL, COLONOSCOPY_OVERALL, USG_ABDOMINAL_OVERALL, HISTOPATH_OVERALL,
                USG_THYROID_OVERALL, ECHO_OVERALL, CHEST_XRAY_OVERALL (imaging/endoscopy overall status)

                CRITICAL — WBC DIFFERENTIAL COUNTS:
                - NEUT/LYMPH/MONO/EOS/BASO: use ONLY when unit is % (percentage value 0-100)
                - NEUT_ABS/LYMPH_ABS/MONO_ABS/EOS_ABS/BASO_ABS: use when unit is tys/µl, G/L, 10^3/µl (absolute count)
                - Example: "Neutrofile 53%" → NEUT; "Neutrofile 3.5 tys/µl" → NEUT_ABS
                - Do NOT use NEUT for absolute counts even if the label just says "Neutrofile"

                UNIT STANDARDIZATION RULES:
                - Cholesterol (CHOL, LDL, HDL, NON_HDL): if in mmol/L, convert to mg/dL (× 38.67)
                - Triglycerides (TG): if in mmol/L, convert to mg/dL (× 88.57)
                - Glucose (GLU): if in mmol/L, convert to mg/dL (× 18.02)
                - CREAT, BILIR, UA: standard unit is µmol/L — do NOT convert, report as-is
                - FT3: standard unit is pg/mL — do NOT convert, report as-is
                - FT4: standard unit is ng/dL — do NOT convert, report as-is
                - When converting: set originalValueNumeric/originalUnit to source values,
                  set valueNumeric/unit to converted values, set conversionApplied to true
                - CRITICAL: when converting a value, ALSO multiply refRangeLow and refRangeHigh
                  by the same factor so they stay in the same unit as the converted value

                CATEGORY FIELD RULES:
                - The "category" field of each result MUST be the exam section's examTypeCode
                - Use the standardized code (e.g. "LIPID_PANEL"), NEVER the document's Polish section
                  name (e.g. NOT "Lipidogram", "Morfologia", "Tarczyca", "Wątroba")
                - If a result belongs to a MORPHOLOGY section, set category = "MORPHOLOGY"
                - If a result belongs to a LIPID_PANEL section, set category = "LIPID_PANEL", etc.

                FLAG DETERMINATION: Do NOT determine flags — leave that to the system.

                DATE EXTRACTION RULES:
                - Extract the exam/collection date as "date" field in YYYY-MM-DD format (e.g. "2024-03-15")
                - "date" contains only the calendar date — no time, no timezone
                - If the document shows a date with time, extract the full datetime into "performedAt"
                  (ISO-8601 with Z suffix, e.g. "2024-03-15T08:30:00Z") and also set "date" to the date part
                - If only a date is visible (no time), set "date" to that date and leave "performedAt" null
                - If no date is found anywhere in the document, set both "date" and "performedAt" to null

                MULTI-SECTION EXTRACTION RULES:
                - A single document may contain multiple distinct exam sections (e.g. Morfologia krwi + Badanie moczu + PT(INR))
                - Extract EACH distinct exam section as a separate entry in the sections[] array
                - Each section has its own examTypeCode, title, and results[]
                - Single-section documents return sections[] with exactly one element
                - Shared metadata (date, laboratory, orderingDoctor) belongs at the top level, NOT inside sections

                ECG EXTRACTION RULES (applies to ECG exam type):
                - ECG_RHYTHM: set valueText to the rhythm classification in Polish (e.g., "Rytm zatokowy",
                  "Migotanie przedsionków"). Set valueNumeric to null.
                - ECG_AVG_HR: average heart rate in bpm during the ECG recording. Numeric value.
                - ECG_DURATION_SEC: recording duration in seconds. Numeric value.
                - ECG_GAIN: amplifier gain setting (e.g., "10mm/mV"). Set as valueText, valueNumeric=null.
                - ECG_PAPER_SPEED: paper speed setting (e.g., "25mm/s"). Set as valueText, valueNumeric=null.
                - Leave results[] for all ECG structural markers (P-wave, QRS, QT/QTc intervals etc.)
                  as separate lab results if found. Use codes from COMMON MARKER CODES if available.

                ULTRASOUND (USG) EXTRACTION RULES (applies to ABDOMINAL_USG, THYROID_USG):
                - Set reportText with the COMPLETE verbatim findings text, formatted as Markdown
                - Set conclusions with a 2-3 sentence medical summary in Polish, formatted as Markdown
                - Leave results[] EMPTY — do NOT add any result entries for USG exams

                ENDOSCOPY / OTHER IMAGING EXTRACTION RULES (applies to GASTROSCOPY, COLONOSCOPY, CHEST_XRAY, ECHO, HISTOPATHOLOGY):
                - Set reportText with the COMPLETE verbatim findings text copied from the document (all organs, observations, measurements), formatted as Markdown
                - Set conclusions with a 2-3 sentence medical summary of the key findings — write in Polish, formatted as Markdown
                - ALSO add exactly ONE status result to results[]:
                    - markerCode: use the exam-specific overall code (e.g. GASTROSCOPY_OVERALL for GASTROSCOPY, ECHO_OVERALL for ECHO,
                      CHEST_XRAY_OVERALL for CHEST_XRAY, COLONOSCOPY_OVERALL for COLONOSCOPY,
                      HISTOPATH_OVERALL for HISTOPATHOLOGY)
                    - valueNumeric: 2 if findings are normal/unremarkable, 1 if minor/watchful findings requiring monitoring,
                                   0 if significant/pathological findings requiring follow-up or treatment
                    - valueText: "Prawidłowe" (when valueNumeric=2) | "Wymaga obserwacji" (when valueNumeric=1) | "Nieprawidłowe" (when valueNumeric=0)
                    - markerName: Polish name matching the exam (e.g. "Wynik gastroskopii", "Wynik echa serca")
                    - category: the examTypeCode (e.g. "GASTROSCOPY")
                    - sortOrder: 1
                    - Leave all other result fields null (no refRangeLow/High — system handles thresholds automatically)

                OUTPUT FORMAT RULES (apply to ALL exam types):
                - reportText: format as Markdown — use bullet points, bold for organ names or key findings, preserve document structure
                - conclusions: always write in Polish, format as Markdown (2-3 sentences for imaging/endoscopy; brief summary paragraph for lab exams)

                IMPORTANT RULES:
                1. Set isMedicalReport=false if document is NOT a medical exam result
                2. For LAB exams (MORPHOLOGY, LIPID_PANEL, THYROID, etc.): extract ALL markers into results[], include reference ranges
                3. For ABDOMINAL_USG and THYROID_USG: leave results[] EMPTY — use reportText + conclusions only
                   For other IMAGING/ENDOSCOPY exams (GASTROSCOPY, COLONOSCOPY, CHEST_XRAY, ECHO, HISTOPATHOLOGY): add exactly ONE overall status result to results[] — use reportText + conclusions for full narrative
                4. For each lab result, always provide markerCode (use the codes above when possible)
                5. Report confidence (0.0-1.0) based on document clarity
                """;
    }

    private String buildUserPrompt(String description, List<MultipartFile> files) {
        var prompt = new StringBuilder("Analyze this medical document and extract structured data.\n");

        if (description != null && !description.isBlank()) {
            var sanitized = guardrailFacade.sanitizeOnly(description, GuardrailProfile.MEDICAL_EXAM_IMPORT);
            prompt.append("Additional context: ").append(sanitized).append("\n");
        }

        if (files != null && !files.isEmpty()) {
            prompt.append("Document files attached (").append(files.size()).append(" file(s)).\n");
        }

        prompt.append("\nExtract all medical data. If the document contains multiple distinct exam sections, return each as a separate entry in sections[].");
        return prompt.toString();
    }

    private void attachFileToSpec(ChatClient.PromptUserSpec userSpec, MultipartFile file) {
        try {
            String mimeType = resolveMimeType(file);
            byte[] bytes = file.getBytes();
            userSpec.media(MimeType.valueOf(mimeType), new ByteArrayResource(bytes));
        } catch (IOException e) {
            log.warn("Failed to read file attachment", e);
        }
    }

    private String resolveMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("application/octet-stream")) {
            return "image/jpg".equals(contentType) ? MIME_JPEG : contentType;
        }
        String filename = file.getOriginalFilename();
        if (filename == null) return MIME_JPEG;
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return MIME_JPEG;
    }

    private ExtractedExamData transformToExtractedData(AiMedicalExamExtractionResponse response) {
        var confidence = BigDecimal.valueOf(response.confidence());

        if (!response.isMedicalReport()) {
            String error = Optional.ofNullable(response.validationError())
                    .orElse("Document is not a medical report");
            return ExtractedExamData.invalid(error, confidence);
        }

        var rawSections = response.sections();
        if (rawSections == null || rawSections.isEmpty()) {
            return ExtractedExamData.invalid("No exam sections found in document", confidence);
        }

        var sections = rawSections.stream()
                .filter(s -> s != null && s.examTypeCode() != null)
                .map(s -> new ExtractedExamData.ExtractedSectionData(
                        s.examTypeCode(),
                        s.title(),
                        s.reportText(),
                        s.conclusions(),
                        transformResults(s.results())))
                .toList();

        if (sections.isEmpty()) {
            return ExtractedExamData.invalid("Missing required fields (examTypeCode) in all sections", confidence);
        }

        LocalDate date = MedicalExamDateParser.parseLocalDate(response.date());
        Instant performedAt = MedicalExamDateParser.parseInstant(response.performedAt());

        return ExtractedExamData.valid(
                date, performedAt, response.laboratory(), response.orderingDoctor(),
                sections, confidence, ExamSource.AI_IMPORT.name());
    }

    private List<ExtractedResultData> transformResults(List<AiMedicalExamExtractionResponse.AiExtractedResult> aiResults) {
        if (aiResults == null || aiResults.isEmpty()) {
            return List.of();
        }
        return aiResults.stream()
                .filter(r -> r != null && r.markerCode() != null)
                .limit(MAX_RESULTS_PER_SECTION)
                .map(r -> new ExtractedResultData(
                        r.markerCode(), r.markerName(), r.category(),
                        r.valueNumeric(), r.unit(),
                        r.originalValueNumeric(), r.originalUnit(), r.conversionApplied(),
                        r.refRangeLow(), r.refRangeHigh(), r.refRangeText(),
                        r.valueText(), r.sortOrder()))
                .toList();
    }
}
