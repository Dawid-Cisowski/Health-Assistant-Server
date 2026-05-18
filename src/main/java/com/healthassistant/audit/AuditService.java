package com.healthassistant.audit;

import com.healthassistant.audit.api.AuditFacade;
import com.healthassistant.audit.api.dto.AuditEventEntry;
import com.healthassistant.audit.api.dto.AuditTimelineResponse;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoredEventData;
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
 * Builds an audit timeline by querying health events.
 *
 * Refactored to use a single optimized date-range query instead of the N+1 pattern.
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

        // Initialize the map with empty lists for each day in the range
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            eventsByDay.put(day, new ArrayList<>());
        }

        Instant startInstant = startDate.atStartOfDay(POLAND_ZONE).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        // Single optimized query to fetch all events for the device in the date range
        List<StoredEventData> events = healthEventsFacade.findEventsForDateRange(deviceId, startInstant, endInstant);

        for (StoredEventData event : events) {
            LocalDate eventDate = LocalDate.ofInstant(event.occurredAt(), POLAND_ZONE);
            if (!eventDate.isBefore(startDate) && !eventDate.isAfter(endDate)) {
                eventsByDay.get(eventDate).add(new AuditEventEntry(
                        event.eventType().value(),
                        event.occurredAt(),
                        event.deviceId().value(),
                        event.idempotencyKey().value()
                ));
            }
        }

        return new AuditTimelineResponse(startDate, endDate, eventsByDay);
    }
}
