package com.healthassistant.audit;

import com.healthassistant.audit.api.dto.AuditTimelineResponse;
import com.healthassistant.audit.api.dto.DailyAuditRecord;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final HealthEventsFacade healthEventsFacade;

    public AuditService(HealthEventsFacade healthEventsFacade) {
        this.healthEventsFacade = healthEventsFacade;
    }

    public AuditTimelineResponse buildTimeline(String deviceId, LocalDate startDate, LocalDate endDate) {
        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Fetch all events for the device in the given date range in a single query
        List<StoredEventData> allEvents = healthEventsFacade.findEventsForDateRange(deviceId, startInstant, endInstant);

        // Group events by day
        Map<LocalDate, List<StoredEventData>> eventsByDay = allEvents.stream()
                .collect(Collectors.groupingBy(event -> 
                        event.occurredAt().atZone(ZoneId.systemDefault()).toLocalDate()));

        List<DailyAuditRecord> dailyRecords = new ArrayList<>();
        
        // Iterate through the date range to ensure every day is represented, even if empty
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            List<StoredEventData> dayEvents = eventsByDay.getOrDefault(currentDate, List.of());
            
            dailyRecords.add(new DailyAuditRecord(
                    currentDate,
                    dayEvents.size(),
                    dayEvents.stream().map(e -> e.eventType().value()).toList()
            ));
            
            currentDate = currentDate.plusDays(1);
        }

        return new AuditTimelineResponse(deviceId, dailyRecords);
    }
}
