package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Single metric comparison between current and previous period")
public record ComparisonMetric(
    @JsonProperty("name")
    @Schema(description = "Metric identifier", example = "steps")
    String name,

    @JsonProperty("displayName")
    @Schema(description = "Human-readable metric name", example = "Kroki")
    String displayName,

    @JsonProperty("currentValue")
    @Schema(description = "Current period value", example = "12340")
    Number currentValue,

    @JsonProperty("previousValue")
    @Schema(description = "Previous period value", example = "8500")
    Number previousValue,

    @JsonProperty("changePercent")
    @Schema(description = "Percentage change (null if previous was 0)", example = "45.2")
    Double changePercent
) {}
