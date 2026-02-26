package com.healthassistant.medicalexamimport;

import com.healthassistant.config.SecurityUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Slf4j
class MedicalExamDateParser {

    private MedicalExamDateParser() {}

    static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse date: {}", SecurityUtils.sanitizeForLog(value));
            return null;
        }
    }

    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            log.debug("Could not parse instant: {}", SecurityUtils.sanitizeForLog(value));
            return null;
        }
    }
}
