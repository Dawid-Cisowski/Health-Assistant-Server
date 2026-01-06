package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.MealsEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class MealsEventsListener {

    private static final String MEAL_V1 = "MealRecorded.v1";

    private final MealsProjector mealsProjector;

    @ApplicationModuleListener
    public void onMealsEventsStored(MealsEventsStoredEvent event) {
        log.info("Meals listener received MealsEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing MealRecorded event: {}", sanitizeForLog(eventData.eventId().value()));
                mealsProjector.projectMeal(eventData);
            } catch (Exception e) {
                log.error("Failed to project meal for event: {}", sanitizeForLog(eventData.eventId().value()), e);
            }
        });

        log.info("Meals listener completed processing {} events", event.events().size());
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var mealDeletions = event.deletions().stream()
                .filter(d -> MEAL_V1.equals(d.targetEventType()))
                .toList();

        var mealCorrections = event.corrections().stream()
                .filter(c -> MEAL_V1.equals(c.targetEventType()) || MEAL_V1.equals(c.correctedEventType()))
                .toList();

        if (mealDeletions.isEmpty() && mealCorrections.isEmpty()) {
            return;
        }

        log.info("Meals listener processing {} deletions and {} corrections",
                mealDeletions.size(), mealCorrections.size());

        mealDeletions.forEach(deletion -> {
            try {
                mealsProjector.deleteByEventId(deletion.targetEventId());
            } catch (Exception e) {
                log.error("Failed to delete meal projection for eventId: {}", sanitizeForLog(deletion.targetEventId()), e);
            }
        });

        mealCorrections.forEach(correction -> {
            try {
                mealsProjector.deleteByEventId(correction.targetEventId());
                if (MEAL_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                    mealsProjector.projectCorrectedMeal(
                            event.deviceId(),
                            correction.correctedPayload(),
                            correction.correctedOccurredAt()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to process correction for meal eventId: {}", sanitizeForLog(correction.targetEventId()), e);
            }
        });
    }

    private String sanitizeForLog(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
