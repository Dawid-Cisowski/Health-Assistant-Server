package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.WorkoutEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutEventsListener {

    private final WorkoutProjector workoutProjector;

    @ApplicationModuleListener
    public void onWorkoutEventsStored(WorkoutEventsStoredEvent event) {
        log.info("Workout listener received WorkoutEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        for (StoredEventData eventData : event.events()) {
            try {
                log.debug("Processing WorkoutRecorded event: {}", eventData.eventId().value());
                workoutProjector.projectWorkout(eventData);
            } catch (Exception e) {
                log.error("Failed to project workout for event: {}", eventData.eventId().value(), e);
            }
        }

        log.info("Workout listener completed processing {} events", event.events().size());
    }
}
