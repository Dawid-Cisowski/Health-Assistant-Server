package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.MedicalExamsFacade;
import com.healthassistant.medicalexams.api.dto.AddLabResultsRequest;
import com.healthassistant.medicalexams.api.dto.CreateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.ExamTypeDefinitionResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationAttachmentResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationSummaryResponse;
import com.healthassistant.medicalexams.api.dto.LabResultResponse;
import com.healthassistant.medicalexams.api.dto.LinkExaminationRequest;
import com.healthassistant.medicalexams.api.dto.MarkerTrendResponse;
import com.healthassistant.medicalexams.api.dto.UpdateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.UpdateLabResultRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/medical-exams")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Medical Examinations", description = "Medical exam records, lab results, and attachments")
class MedicalExamsController {

    private final MedicalExamsFacade medicalExamsFacade;

    @GetMapping
    @Operation(summary = "List examinations", description = "Lists all examinations for the authenticated device with optional filters",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Examinations retrieved"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<List<ExaminationSummaryResponse>> listExaminations(
            @RequestAttribute("deviceId") String deviceId,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("Listing examinations for device {}", maskDeviceId(deviceId));
        var result = medicalExamsFacade.listExaminations(deviceId, specialty, examType, from, to);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/types")
    @Operation(summary = "Get exam types", description = "Lists all available exam type definitions",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    ResponseEntity<List<ExamTypeDefinitionResponse>> getExamTypes() {
        return ResponseEntity.ok(medicalExamsFacade.getExamTypes());
    }

    @GetMapping("/specialties")
    @Operation(summary = "Get specialties", description = "Lists all available medical specialties",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    ResponseEntity<List<String>> getSpecialties() {
        return ResponseEntity.ok(medicalExamsFacade.getSpecialties());
    }

    @PostMapping
    @Operation(summary = "Create examination", description = "Creates a new medical examination record",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Examination created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExaminationDetailResponse> createExamination(
            @RequestAttribute("deviceId") String deviceId,
            @Valid @RequestBody CreateExaminationRequest request) {
        log.info("Creating examination for device {}", maskDeviceId(deviceId));
        var result = medicalExamsFacade.createExamination(deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{examId}")
    @Operation(summary = "Get examination", description = "Retrieves a specific examination with all results and attachments",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Examination retrieved"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExaminationDetailResponse> getExamination(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId) {
        var result = medicalExamsFacade.getExamination(deviceId, examId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{examId}")
    @Operation(summary = "Update examination", description = "Updates an existing examination's metadata",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Examination updated"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExaminationDetailResponse> updateExamination(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @Valid @RequestBody UpdateExaminationRequest request) {
        log.info("Updating examination {} for device {}", examId, maskDeviceId(deviceId));
        var result = medicalExamsFacade.updateExamination(deviceId, examId, request);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{examId}")
    @Operation(summary = "Delete examination", description = "Deletes an examination and all its results and attachments",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Examination deleted"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<Void> deleteExamination(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId) {
        log.info("Deleting examination {} for device {}", examId, maskDeviceId(deviceId));
        medicalExamsFacade.deleteExamination(deviceId, examId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{examId}/results")
    @Operation(summary = "Add lab results", description = "Adds lab results to an existing examination",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Results added"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExaminationDetailResponse> addResults(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @Valid @RequestBody AddLabResultsRequest request) {
        log.info("Adding {} results to examination {} for device {}",
                request.results().size(), examId, maskDeviceId(deviceId));
        var result = medicalExamsFacade.addResults(deviceId, examId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{examId}/results/{resultId}")
    @Operation(summary = "Update lab result", description = "Updates an individual lab result",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Result updated"),
            @ApiResponse(responseCode = "404", description = "Examination or result not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<LabResultResponse> updateResult(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @PathVariable UUID resultId,
            @Valid @RequestBody UpdateLabResultRequest request) {
        log.info("Updating result {} in examination {} for device {}", resultId, examId, maskDeviceId(deviceId));
        var result = medicalExamsFacade.updateResult(deviceId, examId, resultId, request);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{examId}/results/{resultId}")
    @Operation(summary = "Delete lab result", description = "Deletes an individual lab result",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Result deleted"),
            @ApiResponse(responseCode = "404", description = "Examination or result not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<Void> deleteResult(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @PathVariable UUID resultId) {
        log.info("Deleting result {} from examination {} for device {}", resultId, examId, maskDeviceId(deviceId));
        medicalExamsFacade.deleteResult(deviceId, examId, resultId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/markers/{markerCode}/trend")
    @Operation(summary = "Get marker trend", description = "Retrieves historical data points for a specific lab marker",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trend data retrieved"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MarkerTrendResponse> getMarkerTrend(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable String markerCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("Getting marker trend from {} to {} for device {}",
                from, to, maskDeviceId(deviceId));
        var result = medicalExamsFacade.getMarkerTrend(deviceId, markerCode, from, to);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/{examTypeCode}")
    @Operation(summary = "Get examination history", description = "Retrieves examination history for a specific exam type",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Examination history retrieved"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<List<ExaminationSummaryResponse>> getExaminationHistory(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable String examTypeCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("Getting examination history for device {}", maskDeviceId(deviceId));
        var result = medicalExamsFacade.getExaminationHistory(deviceId, examTypeCode, from, to);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/{examId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add attachment", description = "Uploads a file attachment to an examination",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Attachment uploaded"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExaminationAttachmentResponse> addAttachment(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "DOCUMENT") String attachmentType,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean isPrimary) {
        log.info("Adding attachment to examination {} for device {}", examId, maskDeviceId(deviceId));
        var result = medicalExamsFacade.addAttachment(deviceId, examId, file, attachmentType, description, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{examId}/attachments")
    @Operation(summary = "Get attachments", description = "Lists all attachments for an examination",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attachments retrieved"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<List<ExaminationAttachmentResponse>> getAttachments(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId) {
        var result = medicalExamsFacade.getAttachments(deviceId, examId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{examId}/attachments/{attachmentId}")
    @Operation(summary = "Delete attachment", description = "Deletes a specific attachment from an examination",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Attachment deleted"),
            @ApiResponse(responseCode = "404", description = "Examination or attachment not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<Void> deleteAttachment(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @PathVariable UUID attachmentId) {
        log.info("Deleting attachment {} from examination {} for device {}",
                attachmentId, examId, maskDeviceId(deviceId));
        medicalExamsFacade.deleteAttachment(deviceId, examId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{examId}/links")
    @Operation(summary = "Link examinations", description = "Creates a bidirectional link between two examinations",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Link created"),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g. self-link)"),
            @ApiResponse(responseCode = "404", description = "Examination not found"),
            @ApiResponse(responseCode = "409", description = "Link already exists"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExaminationDetailResponse> linkExaminations(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @Valid @RequestBody LinkExaminationRequest request) {
        log.info("Linking examination {} with {} for device {}", examId, request.linkedExaminationId(), maskDeviceId(deviceId));
        var result = medicalExamsFacade.linkExaminations(deviceId, examId, request.linkedExaminationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{examId}/links/{linkedExamId}")
    @Operation(summary = "Unlink examinations", description = "Removes the link between two examinations",
            security = @SecurityRequirement(name = "HmacHeaderAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Link removed"),
            @ApiResponse(responseCode = "404", description = "Examination or link not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<Void> unlinkExaminations(
            @RequestAttribute("deviceId") String deviceId,
            @PathVariable UUID examId,
            @PathVariable UUID linkedExamId) {
        log.info("Unlinking examination {} from {} for device {}", examId, linkedExamId, maskDeviceId(deviceId));
        medicalExamsFacade.unlinkExaminations(deviceId, examId, linkedExamId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ExaminationNotFoundException.class)
    ResponseEntity<Void> handleExaminationNotFound(ExaminationNotFoundException ex) {
        log.warn("Examination not found");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(LabResultNotFoundException.class)
    ResponseEntity<Void> handleLabResultNotFound(LabResultNotFoundException ex) {
        log.warn("Lab result not found");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    ResponseEntity<Void> handleAttachmentNotFound(AttachmentNotFoundException ex) {
        log.warn("Attachment not found");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ExaminationLinkNotFoundException.class)
    ResponseEntity<Void> handleExaminationLinkNotFound(ExaminationLinkNotFoundException ex) {
        log.warn("Examination link not found");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ExaminationLinkAlreadyExistsException.class)
    ResponseEntity<String> handleExaminationLinkAlreadyExists(ExaminationLinkAlreadyExistsException ex) {
        log.warn("Examination link already exists");
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Link already exists between these examinations");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request received");
        return ResponseEntity.badRequest().body("Invalid request parameters");
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) return "****";
        return deviceId.substring(0, 4) + "****" + deviceId.substring(deviceId.length() - 4);
    }
}
