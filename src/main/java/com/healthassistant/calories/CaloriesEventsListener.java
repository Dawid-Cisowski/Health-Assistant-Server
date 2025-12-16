package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.CaloriesEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class CaloriesEventsListener {

    private final CaloriesProjector caloriesProjector;

    @ApplicationModuleListener
    public void onCaloriesEventsStored(CaloriesEventsStoredEvent event) {
        log.info("Calories listener received CaloriesEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        for (StoredEventData eventData : event.events()) {
            try {
                log.debug("Processing ActiveCaloriesBurnedRecorded event: {}", eventData.eventId().value());
                caloriesProjector.projectCalories(eventData);
            } catch (Exception e) {
                log.error("Failed to project calories for event: {}", eventData.eventId().value(), e);
            }
        }

        log.info("Calories listener completed processing {} events", event.events().size());
    }
}
