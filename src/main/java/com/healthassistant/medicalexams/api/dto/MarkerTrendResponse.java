package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarkerTrendResponse(
        String markerCode,
        String markerName,
        String standardUnit,
        BigDecimal defaultRefRangeLow,
        BigDecimal defaultRefRangeHigh,
        List<MarkerDataPoint> dataPoints
) {
}
