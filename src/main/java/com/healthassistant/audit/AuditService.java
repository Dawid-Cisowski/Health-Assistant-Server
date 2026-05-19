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

        List<EventData> allEvents = healthEventsFacade.findEventsByOccurredAtBetween(rangeStart, rangeEnd);

        Map<LocalDate, List<AuditEventEntry>> eventsByDay = startDate.datesUntil(endDate.plusDays(1))
                .collect(Collectors.toMap(
                        date -> date,
                        date -> new ArrayList<>(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        allEvents.stream()
                .filter(event -> deviceId.equals(event.deviceId()))
                .forEach(event -> {
                    LocalDate eventDate = event.occurredAt().atZone(POLAND_ZONE).toLocalDate();
                    if (!eventDate.isBefore(startDate) && !eventDate.isAfter(endDate)) {
                        eventsByDay.get(eventDate).add(new AuditEventEntry(
                                event.eventType(),
                                event.occurredAt(),
                                event.deviceId(),
                                event.idempotencyKey()
                        ));
                    }
                });

        return new AuditTimelineResponse(startDate, endDate, eventsByDay);
    }
}
