package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
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
    public void onEventsStored(EventsStoredEvent event) {
        log.info("Workout listener received EventsStoredEvent with {} events", event.events().size());

        event.events()
                .stream()
                .filter(eventData -> "WorkoutRecorded.v1".equals(eventData.eventType().value()))
                .forEachOrdered(eventData -> {
                    try {
                        log.debug("Processing WorkoutRecorded event: {}", eventData.eventId().value());
                        workoutProjector.projectWorkout(eventData);
                    } catch (Exception e) {
                        log.error("Failed to project workout for event: {}", eventData.eventId().value(), e);
                    }
                });

        log.info("Workout listener completed processing {} events", event.events().size());
    }
}
