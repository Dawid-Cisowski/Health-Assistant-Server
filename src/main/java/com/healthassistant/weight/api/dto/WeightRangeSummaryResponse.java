package com.healthassistant.weight.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Weight summary for a date range")
public record WeightRangeSummaryResponse(
    @JsonProperty("startDate")
    LocalDate startDate,

    @JsonProperty("endDate")
    LocalDate endDate,

    @JsonProperty("measurementCount")
    Integer measurementCount,

    @JsonProperty("daysWithData")
    Integer daysWithData,

    @JsonProperty("startWeight")
    BigDecimal startWeight,

    @JsonProperty("endWeight")
    BigDecimal endWeight,

    @JsonProperty("weightChangeKg")
    BigDecimal weightChangeKg,

    @JsonProperty("minWeight")
    BigDecimal minWeight,

    @JsonProperty("maxWeight")
    BigDecimal maxWeight,

    @JsonProperty("averageWeight")
    BigDecimal averageWeight,

    @JsonProperty("averageBodyFat")
    BigDecimal averageBodyFat,

    @JsonProperty("averageMuscle")
    BigDecimal averageMuscle,

    @JsonProperty("measurements")
    List<WeightMeasurementResponse> measurements
) {}
