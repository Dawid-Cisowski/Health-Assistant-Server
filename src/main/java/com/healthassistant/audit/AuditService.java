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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class AuditService implements AuditFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HealthEventsFacade healthEventsFacade;

    @Override
    @Transactional(readOnly = true)
    public AuditTimelineResponse buildTimeline(String deviceId, LocalDate startDate, LocalDate endDate) {
        Instant rangeStart = startDate.atStartOfDay(POLAND_ZONE).toInstant();
        Instant rangeEnd = endDate.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        List<StoredEventData> events = healthEventsFacade.findEventsForDateRange(deviceId, rangeStart, rangeEnd);

        Map<LocalDate, List<AuditEventEntry>> groupedEvents = events.stream()
                .collect(Collectors.groupingBy(
                        event -> LocalDate.ofInstant(event.occurredAt(), POLAND_ZONE),
                        Collectors.mapping(
                                event -> new AuditEventEntry(
                                        event.eventType().value(),
                                        event.occurredAt(),
                                        event.deviceId().value(),
                                        event.idempotencyKey().value()
                                ),
                                Collectors.toList()
                        )
                ));

        Map<LocalDate, List<AuditEventEntry>> eventsByDay = startDate.datesUntil(endDate.plusDays(1))
                .collect(Collectors.toMap(
                        day -> day,
                        day -> groupedEvents.getOrDefault(day, new ArrayList<>()),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return new AuditTimelineResponse(startDate, endDate, eventsByDay);
    }
}
