package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.WorkoutEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutEventsListener {

    private static final String WORKOUT_V1 = "WorkoutRecorded.v1";

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

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var workoutDeletions = event.deletions().stream()
                .filter(d -> WORKOUT_V1.equals(d.targetEventType()))
                .toList();

        var workoutCorrections = event.corrections().stream()
                .filter(c -> WORKOUT_V1.equals(c.targetEventType()) || WORKOUT_V1.equals(c.correctedEventType()))
                .toList();

        if (workoutDeletions.isEmpty() && workoutCorrections.isEmpty()) {
            return;
        }

        log.info("Workout listener processing {} deletions and {} corrections",
                workoutDeletions.size(), workoutCorrections.size());

        workoutDeletions.forEach(deletion -> {
            try {
                workoutProjector.deleteByEventId(deletion.targetEventId());
            } catch (Exception e) {
                log.error("Failed to delete workout projection for eventId: {}", deletion.targetEventId(), e);
            }
        });

        workoutCorrections.forEach(correction -> {
            try {
                workoutProjector.deleteByEventId(correction.targetEventId());
                if (WORKOUT_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                    workoutProjector.projectCorrectedWorkout(
                            event.deviceId(),
                            correction.correctedPayload(),
                            correction.correctedOccurredAt()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to process correction for workout eventId: {}", correction.targetEventId(), e);
            }
        });
    }
}
