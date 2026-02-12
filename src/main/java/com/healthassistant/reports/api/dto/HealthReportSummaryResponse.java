package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Summary of a health report (for listing)")
public record HealthReportSummaryResponse(
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

    @JsonProperty("shortSummary")
    @Schema(description = "Short goal summary", example = "6/8 celow: Kroki, Sen, Aktywnosc")
    String shortSummary,

    @JsonProperty("goalsAchieved")
    @Schema(description = "Number of goals achieved", example = "6")
    int goalsAchieved,

    @JsonProperty("goalsTotal")
    @Schema(description = "Total number of goals", example = "8")
    int goalsTotal,

    @JsonProperty("generatedAt")
    @Schema(description = "When the report was generated")
    Instant generatedAt
) {}
