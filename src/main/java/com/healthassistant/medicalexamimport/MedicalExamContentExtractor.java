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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class MedicalExamContentExtractor {

    private static final int MAX_RESULTS_PER_EXAM = 100;

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
                LIVER_PANEL, KIDNEY_PANEL, ELECTROLYTES, INFLAMMATION, VITAMINS, HORMONES,
                ABDOMINAL_USG, THYROID_USG, CHEST_XRAY, ECHO, ECG, GASTROSCOPY, COLONOSCOPY, OTHER

                COMMON MARKER CODES (use these when applicable):
                WBC, RBC, HGB, HCT, MCV, MCH, MCHC, PLT, NEUT, LYMPH, MONO, EOS, BASO (morphology)
                CHOL, LDL, HDL, TG (lipid panel)
                TSH, FT3, FT4 (thyroid)
                GLU, HBA1C, INSULIN (glucose)
                ALT, AST, GGT, BILIR, ALP (liver)
                CREAT, UREA, UA, EGFR (kidney)
                CRP, ESR, FERR (inflammation)
                VIT_D, VIT_B12, FE, TIBC (vitamins)
                IGE_TOTAL, IGE_SPECIFIC (allergy)

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

                IMPORTANT RULES:
                1. Set isMedicalReport=false if document is NOT a medical exam result
                2. Extract ALL lab markers you can find, including reference ranges
                3. For descriptive reports (USG, endoscopy), set reportText with the full findings
                4. For each result, always provide markerCode (use the codes above when possible)
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

        prompt.append("\nExtract all medical data.");
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
            return "image/jpg".equals(contentType) ? "image/jpeg" : contentType;
        }
        String filename = file.getOriginalFilename();
        if (filename == null) return "image/jpeg";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private ExtractedExamData transformToExtractedData(AiMedicalExamExtractionResponse response) {
        var confidence = BigDecimal.valueOf(response.confidence());

        if (!response.isMedicalReport()) {
            String error = Optional.ofNullable(response.validationError())
                    .orElse("Document is not a medical report");
            return ExtractedExamData.invalid(error, confidence);
        }

        if (response.examTypeCode() == null || response.title() == null) {
            return ExtractedExamData.invalid("Missing required fields (examTypeCode or title)", confidence);
        }

        LocalDate date = parseLocalDate(response.date());
        Instant performedAt = parseInstant(response.performedAt());
        List<ExtractedResultData> results = transformResults(response.results());

        return ExtractedExamData.valid(
                response.examTypeCode(), response.title(), date, performedAt,
                response.laboratory(), response.orderingDoctor(),
                response.reportText(), response.conclusions(),
                results, confidence, null, null);
    }

    private List<ExtractedResultData> transformResults(List<AiMedicalExamExtractionResponse.AiExtractedResult> aiResults) {
        if (aiResults == null || aiResults.isEmpty()) {
            return List.of();
        }
        return aiResults.stream()
                .filter(r -> r != null && r.markerCode() != null)
                .limit(MAX_RESULTS_PER_EXAM)
                .map(r -> new ExtractedResultData(
                        r.markerCode(), r.markerName(), r.category(),
                        r.valueNumeric(), r.unit(),
                        r.originalValueNumeric(), r.originalUnit(), r.conversionApplied(),
                        r.refRangeLow(), r.refRangeHigh(), r.refRangeText(),
                        r.valueText(), r.sortOrder()))
                .toList();
    }

    private LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse date field from AI response: {}", value);
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            log.debug("Could not parse performedAt: {}", value);
            return null;
        }
    }
}
