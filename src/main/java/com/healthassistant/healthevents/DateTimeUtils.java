package com.healthassistant.healthevents;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class DateTimeUtils {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private DateTimeUtils() {
    }

    static LocalDate toPolandDate(Instant instant) {
        return instant != null ? instant.atZone(POLAND_ZONE).toLocalDate() : null;
    }

    static ZoneId polandZone() {
        return POLAND_ZONE;
    }
}
