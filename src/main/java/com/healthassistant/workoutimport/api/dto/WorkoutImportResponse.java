package com.healthassistant.workoutimport.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response from workout image import")
public record WorkoutImportResponse(
    @JsonProperty("status")
    @Schema(description = "Status of the import", example = "success")
    String status,

    @JsonProperty("workoutId")
    @Schema(description = "Generated workout ID", example = "gymrun-screenshot-2025-12-15-abc12345")
    String workoutId,

    @JsonProperty("eventId")
    @Schema(description = "Event ID if stored successfully")
    String eventId,

    @JsonProperty("performedAt")
    @Schema(description = "When the workout was performed (extracted from image)")
    Instant performedAt,

    @JsonProperty("note")
    @Schema(description = "Workout note/title extracted from image")
    String note,

    @JsonProperty("exerciseCount")
    @Schema(description = "Number of exercises extracted")
    int exerciseCount,

    @JsonProperty("totalSets")
    @Schema(description = "Number of sets extracted")
    int totalSets,

    @JsonProperty("confidence")
    @Schema(description = "AI confidence score for extraction (0.0 to 1.0)")
    double confidence,

    @JsonProperty("workoutDetails")
    @Schema(description = "Full workout details")
    WorkoutDetailResponse workoutDetails,

    @JsonProperty("errorMessage")
    @Schema(description = "Error message if extraction failed", nullable = true)
    String errorMessage
) {
    public static WorkoutImportResponse success(
        String workoutId, String eventId, Instant performedAt, String note,
        int exerciseCount, int totalSets, double confidence, WorkoutDetailResponse details
    ) {
        return new WorkoutImportResponse("success", workoutId, eventId, performedAt, note,
            exerciseCount, totalSets, confidence, details, null);
    }

    public static WorkoutImportResponse failure(String errorMessage) {
        return new WorkoutImportResponse("failed", null, null, null, null,
            0, 0, 0.0, null, errorMessage);
    }
}
