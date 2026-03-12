package com.healthassistant.mealimport;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.MealImportFacade;
import com.healthassistant.mealimport.api.dto.MealDraftResponse;
import com.healthassistant.mealimport.api.dto.MealDraftUpdateRequest;
import com.healthassistant.mealimport.api.dto.MealImportJobResponse;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/meals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meal Import", description = "Import meals from descriptions and/or images")
class MealImportController {

    private final MealImportFacade mealImportFacade;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Submit async meal import job",
        description = "Upload a text description and/or photos of a meal. Returns a jobId for polling. " +
            "AI analyzes the content asynchronously and creates a MealRecorded.v1 event when done. " +
            "At least one of description or images is required.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Import job accepted, poll for status"),
        @ApiResponse(responseCode = "400", description = "Invalid input (no description or images, invalid image type)"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<?> submitImport(
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "images", required = false) List<MultipartFile> images,
        @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Meal import request from device {}, description: {}, images: {}",
            deviceId,
            description != null ? description.length() + " chars" : "none",
            images != null ? images.size() : 0);

        try {
            String jobId = mealImportFacade.submitImportJob(
                description, images, DeviceId.of(deviceId)
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid meal import from device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().body(MealImportResponse.failure(e.getMessage()));
        }
    }

    @PostMapping(value = "/import/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Submit async meal analysis job",
        description = "Upload a text description and/or photos of a meal. Returns a jobId for polling. " +
            "AI analyzes the content asynchronously and creates a draft for review. " +
            "Returns clarifying questions if confidence is low. Does NOT save to database yet.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Analysis job accepted, poll for status"),
        @ApiResponse(responseCode = "400", description = "Invalid input (no description or images, invalid image type)"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<?> submitAnalyze(
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "images", required = false) List<MultipartFile> images,
        @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Meal analysis request from device {}, description: {}, images: {}",
            deviceId,
            description != null ? description.length() + " chars" : "none",
            images != null ? images.size() : 0);

        try {
            String jobId = mealImportFacade.submitAnalyzeJob(
                description, images, DeviceId.of(deviceId)
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid meal analysis from device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().body(MealDraftResponse.failure(e.getMessage()));
        }
    }

    @GetMapping(value = "/import/jobs/{jobId}")
    @Operation(
        summary = "Get meal import job status",
        description = "Poll for the status of an async meal import or analysis job.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job status returned"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealImportJobResponse> getJobStatus(
        @PathVariable UUID jobId,
        @RequestAttribute("deviceId") String deviceId
    ) {
        try {
            MealImportJobResponse response = mealImportFacade.getJobStatus(jobId, DeviceId.of(deviceId));
            return ResponseEntity.ok(response);
        } catch (JobNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping(value = "/import/{draftId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Update meal draft",
        description = "Update extracted meal data, answer clarifying questions, or change the timestamp.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft updated"),
        @ApiResponse(responseCode = "404", description = "Draft not found"),
        @ApiResponse(responseCode = "409", description = "Draft already confirmed"),
        @ApiResponse(responseCode = "410", description = "Draft has expired"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealDraftResponse> updateDraft(
        @PathVariable UUID draftId,
        @RequestBody MealDraftUpdateRequest request,
        @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Draft update request for {} from device {}", draftId, deviceId);

        try {
            MealDraftResponse response = mealImportFacade.updateDraft(
                draftId, request, DeviceId.of(deviceId)
            );
            return ResponseEntity.ok(response);
        } catch (DraftNotFoundException e) {
            log.warn("Draft not found {} for device {}", draftId, deviceId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MealDraftResponse.failure(e.getMessage()));
        } catch (DraftAlreadyConfirmedException e) {
            log.warn("Draft already confirmed {} for device {}", draftId, deviceId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MealDraftResponse.failure(e.getMessage()));
        } catch (DraftExpiredException e) {
            log.warn("Draft expired {} for device {}", draftId, deviceId);
            return ResponseEntity.status(HttpStatus.GONE)
                .body(MealDraftResponse.failure(e.getMessage()));
        }
    }

    @PostMapping(value = "/import/{draftId}/confirm")
    @Operation(
        summary = "Confirm and save meal draft",
        description = "Finalize the draft and create a MealRecorded.v1 event.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Meal saved"),
        @ApiResponse(responseCode = "404", description = "Draft not found"),
        @ApiResponse(responseCode = "409", description = "Draft already confirmed"),
        @ApiResponse(responseCode = "410", description = "Draft has expired"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealImportResponse> confirmDraft(
        @PathVariable UUID draftId,
        @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Draft confirm request for {} from device {}", draftId, deviceId);

        try {
            MealImportResponse response = mealImportFacade.confirmDraft(
                draftId, DeviceId.of(deviceId)
            );
            return ResponseEntity.ok(response);
        } catch (DraftNotFoundException e) {
            log.warn("Draft not found {} for device {}", draftId, deviceId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MealImportResponse.failure(e.getMessage()));
        } catch (DraftAlreadyConfirmedException e) {
            log.warn("Draft already confirmed {} for device {}", draftId, deviceId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MealImportResponse.failure(e.getMessage()));
        } catch (DraftExpiredException e) {
            log.warn("Draft expired {} for device {}", draftId, deviceId);
            return ResponseEntity.status(HttpStatus.GONE)
                .body(MealImportResponse.failure(e.getMessage()));
        }
    }
}
