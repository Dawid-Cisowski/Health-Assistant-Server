package com.healthassistant.bodymeasurements.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateBodyMeasurementRequest(
        BigDecimal bicepsLeftCm,
        BigDecimal bicepsRightCm,
        BigDecimal forearmLeftCm,
        BigDecimal forearmRightCm,
        BigDecimal chestCm,
        BigDecimal waistCm,
        BigDecimal abdomenCm,
        BigDecimal hipsCm,
        BigDecimal neckCm,
        BigDecimal shouldersCm,
        BigDecimal thighLeftCm,
        BigDecimal thighRightCm,
        BigDecimal calfLeftCm,
        BigDecimal calfRightCm,
        Instant measuredAt,
        String notes
) {}
