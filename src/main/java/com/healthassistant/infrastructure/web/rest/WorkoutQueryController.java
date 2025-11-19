package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.workout.query.WorkoutQueryService;
import com.healthassistant.dto.WorkoutDetailResponse;
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
@Tag(name = "Workouts", description = "Query workout details and history")
class WorkoutQueryController {

    private final WorkoutQueryService workoutQueryService;

    @GetMapping("/{workoutId}")
    @Operation(
            summary = "Get workout details",
            description = "Retrieves detailed information about a specific workout including exercises and sets. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workout found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "Workout not found")
    })
    ResponseEntity<WorkoutDetailResponse> getWorkoutDetails(@PathVariable String workoutId) {
        log.info("Retrieving workout details for workoutId: {}", workoutId);
        return workoutQueryService.getWorkoutDetails(workoutId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(
            summary = "Get workouts in date range",
            description = "Retrieves all workouts within the specified date range. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workouts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "400", description = "Invalid date parameters")
    })
    ResponseEntity<List<WorkoutDetailResponse>> getWorkouts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.info("Retrieving workouts from {} to {}", from, to);

        if (from.isAfter(to)) {
            log.warn("Invalid date range: from {} is after to {}", from, to);
            return ResponseEntity.badRequest().build();
        }

        List<WorkoutDetailResponse> workouts = workoutQueryService.getWorkoutsByDateRange(from, to);
        return ResponseEntity.ok(workouts);
    }
}
