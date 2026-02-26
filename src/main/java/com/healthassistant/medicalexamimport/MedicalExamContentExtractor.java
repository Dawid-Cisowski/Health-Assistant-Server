package com.healthassistant.medicalexamimport;

import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;
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
                ABDOMINAL_USG, THYROID_USG, CHEST_XRAY, ECHO, ECG, GASTROSCOPY, COLONOSCOPY, OTHER

                COMMON MARKER CODES (use these when applicable, for LAB exams only):
                WBC, RBC, HGB, HCT, MCV, MCH, MCHC, PLT, NEUT, LYMPH, MONO, EOS, BASO (morphology)
                CHOL, LDL, HDL, TG (lipid panel)
                TSH, FT3, FT4 (thyroid)
                GLU, HBA1C, INSULIN (glucose)
                ALT, AST, GGT, BILIR, ALP (liver)
                CREAT, UREA, UA, EGFR (kidney)
                CRP, ESR, FERR (inflammation)
                VIT_D, VIT_B12, FE, TIBC (vitamins)
                IGE_TOTAL, IGE_SPECIFIC (allergy)
                PT, INR, APTT, FIBRINOGEN (coagulation)

                UNIT STANDARDIZATION RULES:
                - Cholesterol (CHOL, LDL, HDL): if in mmol/L, convert to mg/dL (multiply by 38.67)
                - Triglycerides (TG): if in mmol/L, convert to mg/dL (multiply by 88.57)
                - Glucose (GLU): if in mmol/L, convert to mg/dL (multiply by 18.02)
                - When converting: set originalValueNumeric and originalUnit to the source values,
                  set valueNumeric and unit to the converted values, set conversionApplied to true

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

                ENDOSCOPY / IMAGING EXTRACTION RULES (applies to GASTROSCOPY, COLONOSCOPY, ABDOMINAL_USG, THYROID_USG, CHEST_XRAY, ECHO):
                - Set reportText with the COMPLETE verbatim findings text copied from the document (all organs, observations, measurements), formatted as Markdown
                - Set conclusions with a 2-3 sentence medical summary of the key findings — write in Polish, formatted as Markdown
                - Leave results[] EMPTY — do NOT extract any numeric markers or findings into results for these exam types
                - The full clinical value is in reportText + conclusions, not in structured markers

                OUTPUT FORMAT RULES (apply to ALL exam types):
                - reportText: format as Markdown — use bullet points, bold for organ names or key findings, preserve document structure
                - conclusions: always write in Polish, format as Markdown (2-3 sentences for imaging/endoscopy; brief summary paragraph for lab exams)

                IMPORTANT RULES:
                1. Set isMedicalReport=false if document is NOT a medical exam result
                2. For LAB exams (MORPHOLOGY, LIPID_PANEL, THYROID, etc.): extract ALL markers into results[], include reference ranges
                3. For IMAGING/ENDOSCOPY exams (GASTROSCOPY, COLONOSCOPY, ABDOMINAL_USG, THYROID_USG, CHEST_XRAY, ECHO): results[] MUST be empty — use reportText + conclusions only
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
                sections, confidence);
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
