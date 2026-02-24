package com.healthassistant.medicalexams.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MarkerDataPoint(
        LocalDate date,
        BigDecimal value,
        String flag,
        UUID examId,
        String examTitle,
        BigDecimal labRefRangeLow,
        BigDecimal labRefRangeHigh
) {
}
