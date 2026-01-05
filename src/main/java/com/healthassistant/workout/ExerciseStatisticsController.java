package com.healthassistant.workout;

import com.healthassistant.workout.api.dto.ExerciseStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@RequestMapping("/v1/exercises")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Exercise Statistics", description = "Exercise statistics and progression tracking")
class ExerciseStatisticsController {

    private final ExerciseStatisticsService statisticsService;

    @GetMapping("/{exerciseId}/statistics")
    @Operation(
            summary = "Get exercise statistics",
            description = "Retrieves summary statistics, progression data, and workout history for a specific exercise",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "204", description = "Exercise exists but user has no workout data"),
            @ApiResponse(responseCode = "404", description = "Exercise not found in catalog"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ExerciseStatisticsResponse> getExerciseStatistics(
            @PathVariable
            @Parameter(description = "Exercise ID from catalog (e.g., chest_1)", example = "chest_1")
            String exerciseId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Start date for filtering history (optional)", example = "2024-01-01")
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "End date for filtering history (optional)", example = "2024-12-31")
            LocalDate toDate,

            @RequestHeader("X-Device-Id")
            @Parameter(description = "Device identifier for HMAC auth", hidden = true)
            String deviceId
    ) {
        log.info("Retrieving exercise statistics for exerciseId: {}, device: {}, dateRange: {} to {}",
                exerciseId, deviceId, fromDate, toDate);

        return statisticsService.getStatistics(deviceId, exerciseId, fromDate, toDate)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    if (statisticsService.exerciseExistsInCatalog(exerciseId)) {
                        return ResponseEntity.noContent().build();
                    }
                    return ResponseEntity.notFound().build();
                });
    }
}
