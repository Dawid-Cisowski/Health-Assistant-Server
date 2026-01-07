package com.healthassistant.workoutimport;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.workoutimport.api.WorkoutImportFacade;
import com.healthassistant.workoutimport.api.dto.WorkoutImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workout Import", description = "Import workouts from images")
class WorkoutImportController {

    private final WorkoutImportFacade workoutImportFacade;

    @PostMapping(value = "/import-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Import workout from GymRun screenshot",
        description = "Upload a GymRun workout summary screenshot. AI extracts workout data and creates a WorkoutRecorded.v1 event.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workout extraction processed (check status field)"),
        @ApiResponse(responseCode = "400", description = "Invalid image file"),
        @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<WorkoutImportResponse> importWorkoutFromImage(
        @RequestParam("image") MultipartFile image,
        @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Workout image import request from device {}, file: {}, size: {} bytes",
            maskDeviceId(deviceId), sanitizeForLog(image.getOriginalFilename()), image.getSize());

        try {
            WorkoutImportResponse response = workoutImportFacade.importFromImage(
                image, DeviceId.of(deviceId)
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid image from device {}: {}", maskDeviceId(deviceId), sanitizeForLog(e.getMessage()));
            String safeMessage = mapToSafeErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(WorkoutImportResponse.failure(safeMessage));
        }
    }

    private String mapToSafeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "Invalid image file";
        }
        if (errorMessage.contains("empty")) {
            return "Image file is empty";
        }
        if (errorMessage.contains("size")) {
            return "Image file exceeds maximum size";
        }
        if (errorMessage.contains("type")) {
            return "Invalid image type";
        }
        return "Invalid image file";
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        return input.replaceAll("[\\n\\r\\t]", "_");
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }
}
