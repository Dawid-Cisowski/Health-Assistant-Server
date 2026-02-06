package com.healthassistant.dailysummary.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AI-generated detailed health report in Markdown format")
public record AiHealthReportResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of the report period", example = "2026-02-05")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of the report period", example = "2026-02-05")
    LocalDate endDate,

    @JsonProperty("report")
    @Schema(description = "AI-generated health report in Markdown format")
    String report,

    @JsonProperty("dataAvailable")
    @Schema(description = "Whether health data was available for the period")
    boolean dataAvailable
) {
    public static AiHealthReportResponse noData(LocalDate startDate, LocalDate endDate) {
        return new AiHealthReportResponse(startDate, endDate, "Brak danych za ten okres", false);
    }

    public static AiHealthReportResponse daily(LocalDate date, String report, boolean dataAvailable) {
        return new AiHealthReportResponse(date, date, report, dataAvailable);
    }

    public static AiHealthReportResponse range(LocalDate startDate, LocalDate endDate, String report, boolean dataAvailable) {
        return new AiHealthReportResponse(startDate, endDate, report, dataAvailable);
    }
}
