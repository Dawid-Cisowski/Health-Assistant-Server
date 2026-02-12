package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a single goal evaluation")
public record GoalResult(
    @JsonProperty("name")
    @Schema(description = "Goal identifier", example = "steps")
    String name,

    @JsonProperty("displayName")
    @Schema(description = "Human-readable goal name", example = "Kroki")
    String displayName,

    @JsonProperty("achieved")
    @Schema(description = "Whether the goal was achieved", example = "true")
    boolean achieved,

    @JsonProperty("actualValue")
    @Schema(description = "Actual value achieved (formatted)", example = "12,340")
    String actualValue,

    @JsonProperty("targetValue")
    @Schema(description = "Target value description", example = ">= 10,000")
    String targetValue
) {}
