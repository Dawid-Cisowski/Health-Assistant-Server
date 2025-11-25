package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class CaloriesEventsListener {

    private final CaloriesProjector caloriesProjector;

    @EventListener
    public void onEventsStored(EventsStoredEvent event) {
        log.info("Calories listener received EventsStoredEvent with {} events", event.events().size());

        for (StoredEventData eventData : event.events()) {
            String eventType = eventData.eventType().value();
            if ("ActiveCaloriesBurnedRecorded.v1".equals(eventType)) {
                try {
                    log.debug("Processing ActiveCaloriesBurnedRecorded event: {}", eventData.eventId().value());
                    caloriesProjector.projectCalories(eventData);
                } catch (Exception e) {
                    log.error("Failed to project calories for event: {}", eventData.eventId().value(), e);
                }
            }
        }

        log.info("Calories listener completed processing {} events", event.events().size());
    }
}
