package com.healthassistant.bodymeasurements.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Body measurements summary for a date range")
public record BodyMeasurementRangeSummaryResponse(
    @JsonProperty("startDate")
    LocalDate startDate,

    @JsonProperty("endDate")
    LocalDate endDate,

    @JsonProperty("measurementCount")
    Integer measurementCount,

    @JsonProperty("daysWithData")
    Integer daysWithData,

    @JsonProperty("measurements")
    List<BodyMeasurementResponse> measurements
) {}
