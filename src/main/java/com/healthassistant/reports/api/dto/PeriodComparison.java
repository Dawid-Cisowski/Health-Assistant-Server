package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Comparison of current period with previous period")
public record PeriodComparison(
    @JsonProperty("previousPeriodStart")
    @Schema(description = "Start date of previous period", example = "2026-02-10")
    LocalDate previousPeriodStart,

    @JsonProperty("previousPeriodEnd")
    @Schema(description = "End date of previous period", example = "2026-02-10")
    LocalDate previousPeriodEnd,

    @JsonProperty("metrics")
    @Schema(description = "Comparison metrics")
    List<ComparisonMetric> metrics
) {}
