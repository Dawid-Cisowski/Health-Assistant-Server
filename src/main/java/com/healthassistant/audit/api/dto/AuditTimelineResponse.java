package com.healthassistant.audit.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AuditTimelineResponse(
        LocalDate startDate,
        LocalDate endDate,
        Map<LocalDate, List<AuditEventEntry>> eventsByDay
) {
}
