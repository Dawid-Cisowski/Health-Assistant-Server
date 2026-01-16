package com.healthassistant.workout;

import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse;
import com.healthassistant.workout.api.dto.UpdateWorkoutRequest;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import com.healthassistant.workout.api.dto.WorkoutMutationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Workouts", description = "Workout query and analytics endpoints")
class WorkoutController {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,128}$");
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");

    private final WorkoutFacade workoutFacade;
    private final ExerciseCatalog exerciseCatalog;

    @GetMapping("/{workoutId}")
    @Operation(
            summary = "Get workout details",
            description = "Retrieves complete workout details including all exercises and sets",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workout found"),
            @ApiResponse(responseCode = "404", description = "Workout not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<WorkoutDetailResponse> getWorkoutDetails(
            @PathVariable String workoutId,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving workout details for workoutId: {} deviceId: {}", workoutId, maskDeviceId(deviceId));
        return workoutFacade.getWorkoutDetails(deviceId, workoutId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(
            summary = "Get workouts by date range",
            description = "Retrieves all workouts within the specified date range",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workouts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<List<WorkoutDetailResponse>> getWorkoutsByDateRange(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving workouts for device {} date range: {} to {}", maskDeviceId(deviceId), from, to);
        List<WorkoutDetailResponse> workouts = workoutFacade.getWorkoutsByDateRange(deviceId, from, to);
        return ResponseEntity.ok(workouts);
    }

    @GetMapping("/personal-records")
    @Operation(
            summary = "Get all personal records",
            description = "Returns all personal records (max weights) across all exercises for the authenticated user. Warmup sets are excluded.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Personal records retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<PersonalRecordsResponse> getAllPersonalRecords(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving all personal records for device: {}", maskDeviceId(deviceId));
        PersonalRecordsResponse response = workoutFacade.getAllPersonalRecords(deviceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/exercises")
    @Operation(
            summary = "Get all exercises",
            description = "Returns the complete catalog of available exercises with muscle mappings"
    )
    @ApiResponse(responseCode = "200", description = "Exercise catalog retrieved successfully")
    ResponseEntity<List<ExerciseDefinition>> getAllExercises() {
        log.info("Retrieving exercise catalog");
        return ResponseEntity.ok(exerciseCatalog.getAllExercises());
    }

    @GetMapping("/exercises/muscles")
    @Operation(
            summary = "Get all muscle groups",
            description = "Returns the list of all available muscle groups for filtering exercises"
    )
    @ApiResponse(responseCode = "200", description = "Muscle groups retrieved successfully")
    ResponseEntity<List<String>> getMuscleGroups() {
        log.info("Retrieving muscle groups");
        return ResponseEntity.ok(exerciseCatalog.getMuscleGroups());
    }

    @GetMapping("/exercises/muscle/{muscle}")
    @Operation(
            summary = "Get exercises by muscle",
            description = "Returns all exercises that engage the specified muscle group"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully")
    })
    ResponseEntity<List<ExerciseDefinition>> getExercisesByMuscle(@PathVariable String muscle) {
        log.info("Retrieving exercises for muscle: {}", sanitizeForLog(muscle));
        return ResponseEntity.ok(exerciseCatalog.getExercisesByMuscle(muscle));
    }

    @DeleteMapping("/event/{eventId}")
    @Operation(
            summary = "Delete a workout",
            description = "Deletes an existing workout record by its event ID",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Workout deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "Workout not found")
    })
    ResponseEntity<Void> deleteWorkout(
            @PathVariable @NotBlank String eventId,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);
        validateEventId(eventId);
        log.info("Deleting workout {} for device {}", sanitizeForLog(eventId), maskDeviceId(deviceId));
        workoutFacade.deleteWorkout(deviceId, eventId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/event/{eventId}")
    @Operation(
            summary = "Update a workout",
            description = "Updates an existing workout record. All fields are required. performedAt can be up to 30 days in the past.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workout updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or backdating validation failed"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "Workout not found")
    })
    ResponseEntity<WorkoutMutationResponse> updateWorkout(
            @PathVariable @NotBlank String eventId,
            @Valid @RequestBody UpdateWorkoutRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        validateDeviceId(deviceId);
        validateEventId(eventId);
        log.info("Updating workout {} for device {}", sanitizeForLog(eventId), maskDeviceId(deviceId));
        WorkoutMutationResponse response = workoutFacade.updateWorkout(deviceId, eventId, request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(WorkoutNotFoundException.class)
    ResponseEntity<Void> handleWorkoutNotFound(WorkoutNotFoundException ex) {
        log.warn("Workout not found");
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(WorkoutBackdatingValidationException.class)
    ResponseEntity<String> handleBackdatingValidation(WorkoutBackdatingValidationException ex) {
        log.warn("Backdating validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    private void validateDeviceId(String deviceId) {
        if (deviceId == null || !DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("Invalid device ID format");
        }
    }

    private void validateEventId(String eventId) {
        if (eventId == null || !EVENT_ID_PATTERN.matcher(eventId).matches()) {
            throw new IllegalArgumentException("Invalid event ID format");
        }
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        return input.replaceAll("[\\r\\n\\t]", "_");
    }
}
