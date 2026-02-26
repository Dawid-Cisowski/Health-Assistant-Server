package com.healthassistant.medicalexamimport;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.medicalexamimport.api.MedicalExamImportFacade;
import com.healthassistant.medicalexamimport.api.dto.ConfirmDraftRequest;
import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftResponse;
import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftUpdateRequest;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/medical-exams/import")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Medical Exam Import", description = "AI-powered medical examination import from images and PDFs")
class MedicalExamImportController {

    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024L;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_OCTET_STREAM = "application/octet-stream";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MIME_JPEG, "image/png", "image/webp", "application/pdf",
            "text/xml", "application/xml"
    );

    private final MedicalExamImportFacade medicalExamImportFacade;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Analyze medical document",
            description = "Upload medical exam images or PDFs. AI extracts exam type, lab results, " +
                    "and other structured data into a draft for review before saving.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Draft created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or non-medical document"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MedicalExamDraftResponse> analyzeExam(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("deviceId") String deviceId) {
        log.info("Medical exam analysis request from device {}, files: {}, description: {}",
                SecurityUtils.maskDeviceId(deviceId),
                files != null ? files.size() : 0,
                description != null ? description.length() + " chars" : "none");

        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(MedicalExamDraftResponse.failure("Description exceeds maximum allowed length"));
        }

        try {
            validateFiles(files);
            var response = medicalExamImportFacade.analyzeExam(description, files, deviceId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid medical exam import from device {}", SecurityUtils.maskDeviceId(deviceId));
            return ResponseEntity.badRequest().body(MedicalExamDraftResponse.failure("Invalid input"));
        } catch (MedicalExamExtractionException e) {
            log.warn("AI extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId), e.getMessage());
            return ResponseEntity.badRequest().body(MedicalExamDraftResponse.failure("Failed to process medical document"));
        }
    }

    @GetMapping("/{draftId}")
    @Operation(
            summary = "Get draft",
            description = "Retrieve an existing import draft by ID",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Draft retrieved"),
            @ApiResponse(responseCode = "404", description = "Draft not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MedicalExamDraftResponse> getDraft(
            @PathVariable UUID draftId,
            @RequestAttribute("deviceId") String deviceId) {
        try {
            var response = medicalExamImportFacade.getDraft(draftId, deviceId);
            return ResponseEntity.ok(response);
        } catch (DraftNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MedicalExamDraftResponse.failure("Draft not found"));
        }
    }

    @PatchMapping(value = "/{draftId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update draft",
            description = "Modify extracted data in a draft before confirming",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Draft updated"),
            @ApiResponse(responseCode = "404", description = "Draft not found"),
            @ApiResponse(responseCode = "409", description = "Draft already confirmed"),
            @ApiResponse(responseCode = "410", description = "Draft has expired"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MedicalExamDraftResponse> updateDraft(
            @PathVariable UUID draftId,
            @Valid @RequestBody MedicalExamDraftUpdateRequest request,
            @RequestAttribute("deviceId") String deviceId) {
        log.info("Draft update request for {} from device {}", draftId, SecurityUtils.maskDeviceId(deviceId));

        try {
            var response = medicalExamImportFacade.updateDraft(draftId, request, deviceId);
            return ResponseEntity.ok(response);
        } catch (DraftNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MedicalExamDraftResponse.failure("Draft not found"));
        } catch (DraftAlreadyConfirmedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(MedicalExamDraftResponse.failure("Draft has already been confirmed"));
        } catch (DraftExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(MedicalExamDraftResponse.failure("Draft has expired"));
        }
    }

    @PostMapping("/{draftId}/confirm")
    @Operation(
            summary = "Confirm draft",
            description = "Finalize an import draft and save it as one or more medical examination records (one per section)",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Examinations saved"),
            @ApiResponse(responseCode = "404", description = "Draft not found"),
            @ApiResponse(responseCode = "409", description = "Draft already confirmed"),
            @ApiResponse(responseCode = "410", description = "Draft has expired"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<List<ExaminationDetailResponse>> confirmDraft(
            @PathVariable UUID draftId,
            @RequestAttribute("deviceId") String deviceId,
            @Valid @RequestBody(required = false) ConfirmDraftRequest request) {
        log.info("Draft confirm request for {} from device {}", draftId, SecurityUtils.maskDeviceId(deviceId));

        try {
            var relatedId = request != null ? request.relatedExaminationId() : null;
            var result = medicalExamImportFacade.confirmDraft(draftId, deviceId, relatedId);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (DraftNotFoundException e) {
            log.warn("Draft not found {} for device {}", draftId, SecurityUtils.maskDeviceId(deviceId));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (DraftAlreadyConfirmedException e) {
            log.warn("Draft already confirmed {} for device {}", draftId, SecurityUtils.maskDeviceId(deviceId));
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (DraftExpiredException e) {
            log.warn("Draft expired {} for device {}", draftId, SecurityUtils.maskDeviceId(deviceId));
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        files.forEach(file -> {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("File too large");
            }
            var filename = file.getOriginalFilename();
            var effectiveContentType = resolveEffectiveContentType(file.getContentType(), filename);
            if (!ALLOWED_CONTENT_TYPES.contains(effectiveContentType)) {
                throw new IllegalArgumentException("Unsupported file type");
            }
            if (filename != null && (filename.contains("..") || filename.contains("/") || filename.contains("\\"))) {
                throw new IllegalArgumentException("Invalid filename");
            }
        });
    }

    private String resolveEffectiveContentType(String contentType, String filename) {
        if (contentType == null || contentType.equals(MIME_OCTET_STREAM)) {
            return resolveFromFilename(filename);
        }
        if ("image/jpg".equals(contentType)) return MIME_JPEG;
        return contentType;
    }

    private static String resolveFromFilename(String filename) {
        if (filename == null) return MIME_OCTET_STREAM;
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MIME_JPEG;
        if (lower.endsWith(".cda") || lower.endsWith(".xml")) return "text/xml";
        return MIME_OCTET_STREAM;
    }

}
