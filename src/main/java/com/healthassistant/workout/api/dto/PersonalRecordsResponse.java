package com.healthassistant.workout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "All personal records (max weights) across all exercises for a user")
public record PersonalRecordsResponse(
    @JsonProperty("personalRecords")
    @Schema(description = "List of personal records, one per exercise")
    List<PersonalRecordEntry> personalRecords
) {

    @Schema(description = "Personal record entry for a single exercise")
    public record PersonalRecordEntry(
        @JsonProperty("exerciseId")
        @Schema(description = "Exercise catalog ID (null if not mapped)", example = "chest_1")
        String exerciseId,

        @JsonProperty("exerciseName")
        @Schema(description = "Exercise display name", example = "Wyciskanie sztangi leżąc (płasko)")
        String exerciseName,

        @JsonProperty("muscleGroup")
        @Schema(description = "Primary muscle group targeted", example = "CHEST")
        String muscleGroup,

        @JsonProperty("maxWeightKg")
        @Schema(description = "Personal record weight in kg", example = "105.0")
        BigDecimal maxWeightKg,

        @JsonProperty("prDate")
        @Schema(description = "Date when personal record was achieved", example = "2024-12-10")
        LocalDate prDate
    ) {}
}
