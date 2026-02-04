package com.healthassistant.bodymeasurements;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record BodyMeasurement(
    String deviceId,
    String eventId,
    String measurementId,
    LocalDate date,
    Instant measuredAt,
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
    String notes
) {
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    BodyMeasurement {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(measurementId, "measurementId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(measuredAt, "measuredAt cannot be null");
    }

    static BodyMeasurement create(
            String deviceId,
            String eventId,
            String measurementId,
            Instant measuredAt,
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
            String notes
    ) {
        LocalDate date = measuredAt.atZone(POLAND_ZONE).toLocalDate();
        return new BodyMeasurement(
                deviceId, eventId, measurementId, date, measuredAt,
                bicepsLeftCm, bicepsRightCm, forearmLeftCm, forearmRightCm,
                chestCm, waistCm, abdomenCm, hipsCm, neckCm, shouldersCm,
                thighLeftCm, thighRightCm, calfLeftCm, calfRightCm, notes
        );
    }
}
