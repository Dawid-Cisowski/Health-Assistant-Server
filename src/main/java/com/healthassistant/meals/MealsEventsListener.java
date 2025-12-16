package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.MealsEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class MealsEventsListener {

    private final MealsProjector mealsProjector;

    @ApplicationModuleListener
    public void onMealsEventsStored(MealsEventsStoredEvent event) {
        log.info("Meals listener received MealsEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        for (StoredEventData eventData : event.events()) {
            try {
                log.debug("Processing MealRecorded event: {}", eventData.eventId().value());
                mealsProjector.projectMeal(eventData);
            } catch (Exception e) {
                log.error("Failed to project meal for event: {}", eventData.eventId().value(), e);
            }
        }

        log.info("Meals listener completed processing {} events", event.events().size());
    }
}
