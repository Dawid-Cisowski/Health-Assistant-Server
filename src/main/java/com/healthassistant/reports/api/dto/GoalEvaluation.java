package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Evaluation of all goals for a report period")
public record GoalEvaluation(
    @JsonProperty("details")
    @Schema(description = "Individual goal results")
    List<GoalResult> details,

    @JsonProperty("achieved")
    @Schema(description = "Number of goals achieved", example = "6")
    int achieved,

    @JsonProperty("total")
    @Schema(description = "Total number of goals", example = "8")
    int total
) {}
