package com.healthassistant.heartrate.api.dto;

import java.time.LocalDate;
import java.util.List;

public record HeartRateRangeResponse(
        LocalDate startDate,
        LocalDate endDate,
        Integer totalDataPoints,
        Integer avgBpm,
        Integer minBpm,
        Integer maxBpm,
        List<HeartRateDataPointResponse> dataPoints
) {
}
