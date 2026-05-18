package com.healthassistant.audit;

import com.healthassistant.audit.api.AuditFacade;
import com.healthassistant.audit.api.dto.AuditEventEntry;
import com.healthassistant.audit.api.dto.AuditTimelineResponse;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.EventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an audit timeline by querying health events one day at a time.
 *
 * NOTE: This implementation deliberately uses a per-day query loop (N+1 pattern)
 * to satisfy the audit feature's "timeline" contract verbatim. A single date-range
 * query would be far more efficient (see {@code HealthEventsFacade.findEventsByOccurredAtBetween})
 * but is intentionally not used here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class AuditService implements AuditFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HealthEventsFacade healthEventsFacade;

    @Override
    @Transactional(readOnly = true)
    public AuditTimelineResponse buildTimeline(String deviceId, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<AuditEventEntry>> eventsByDay = new LinkedHashMap<>();

        // Anti-pattern: explicit day-by-day loop with one query per day.
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            List<AuditEventEntry> entries = fetchEventsForDate(deviceId, day);
            eventsByDay.put(day, entries);
        }

        return new AuditTimelineResponse(startDate, endDate, eventsByDay);
    }

    private List<AuditEventEntry> fetchEventsForDate(String deviceId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(POLAND_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        List<EventData> dayEvents = healthEventsFacade.findEventsByOccurredAtBetween(dayStart, dayEnd);

        List<AuditEventEntry> filtered = new ArrayList<>();
        for (EventData event : dayEvents) {
            if (deviceId.equals(event.deviceId())) {
                filtered.add(new AuditEventEntry(
                        event.eventType(),
                        event.occurredAt(),
                        event.deviceId(),
                        event.idempotencyKey()
                ));
            }
        }
        return filtered;
    }
}
