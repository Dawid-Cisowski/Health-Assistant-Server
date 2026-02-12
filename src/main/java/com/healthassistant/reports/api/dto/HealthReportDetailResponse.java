package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Full health report with AI summary, goals, comparison, and raw data")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthReportDetailResponse(
    @JsonProperty("reportId")
    @Schema(description = "Report identifier", example = "123")
    long reportId,

    @JsonProperty("reportType")
    @Schema(description = "Type of report", example = "DAILY")
    ReportType reportType,

    @JsonProperty("periodStart")
    @Schema(description = "Start date of the report period", example = "2026-02-11")
    LocalDate periodStart,

    @JsonProperty("periodEnd")
    @Schema(description = "End date of the report period", example = "2026-02-11")
    LocalDate periodEnd,

    @JsonProperty("generatedAt")
    @Schema(description = "When the report was generated")
    Instant generatedAt,

    @JsonProperty("shortSummary")
    @Schema(description = "Short goal summary")
    String shortSummary,

    @JsonProperty("aiSummary")
    @Schema(description = "AI-generated commentary on goals, data, and comparisons")
    String aiSummary,

    @JsonProperty("goals")
    @Schema(description = "Structured goal evaluation")
    GoalEvaluation goals,

    @JsonProperty("comparison")
    @Schema(description = "Comparison with previous period")
    PeriodComparison comparison,

    @JsonProperty("data")
    @Schema(description = "Raw daily health data snapshot (for DAILY reports)")
    ReportDataSnapshot data,

    @JsonProperty("rangeData")
    @Schema(description = "Aggregated range health data snapshot (for WEEKLY/MONTHLY reports)")
    RangeReportDataSnapshot rangeData
) {}
