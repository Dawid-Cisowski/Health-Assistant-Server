package com.healthassistant.workout;

import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workouts", description = "Workout query and analytics endpoints")
class WorkoutController {

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
    ResponseEntity<WorkoutDetailResponse> getWorkoutDetails(@PathVariable String workoutId) {
        log.info("Retrieving workout details for workoutId: {}", workoutId);
        return workoutFacade.getWorkoutDetails(workoutId)
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
        log.info("Retrieving workouts for device {} date range: {} to {}", deviceId, from, to);
        List<WorkoutDetailResponse> workouts = workoutFacade.getWorkoutsByDateRange(deviceId, from, to);
        return ResponseEntity.ok(workouts);
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
        log.info("Retrieving exercises for muscle: {}", muscle);
        return ResponseEntity.ok(exerciseCatalog.getExercisesByMuscle(muscle));
    }
}
