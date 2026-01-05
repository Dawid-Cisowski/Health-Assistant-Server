package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.events.CaloriesEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
class CaloriesEventsListener {

    private static final String ACTIVE_CALORIES_V1 = "ActiveCaloriesBurnedRecorded.v1";

    private final CaloriesProjector caloriesProjector;

    @ApplicationModuleListener
    public void onCaloriesEventsStored(CaloriesEventsStoredEvent event) {
        log.info("Calories listener received CaloriesEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing ActiveCaloriesBurnedRecorded event: {}", eventData.eventId().value());
                caloriesProjector.projectCalories(eventData);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Version conflict while projecting calories for event: {}, skipping", eventData.eventId().value());
            } catch (DataAccessException e) {
                log.error("Database error while projecting calories for event: {}", eventData.eventId().value(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error while projecting calories for event: {}", eventData.eventId().value(), e);
                throw e;
            }
        });

        log.info("Calories listener completed processing {} events", event.events().size());
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var caloriesDeletions = event.deletions().stream()
                .filter(d -> ACTIVE_CALORIES_V1.equals(d.targetEventType()))
                .toList();

        var caloriesCorrections = event.corrections().stream()
                .filter(c -> ACTIVE_CALORIES_V1.equals(c.targetEventType()) || ACTIVE_CALORIES_V1.equals(c.correctedEventType()))
                .toList();

        if (caloriesDeletions.isEmpty() && caloriesCorrections.isEmpty()) {
            return;
        }

        log.info("Calories listener processing {} deletions and {} corrections",
                caloriesDeletions.size(), caloriesCorrections.size());

        Set<LocalDate> affectedDates = new HashSet<>();

        caloriesDeletions.forEach(deletion -> {
            affectedDates.addAll(deletion.affectedDates());
        });

        caloriesCorrections.forEach(correction -> {
            affectedDates.addAll(correction.affectedDates());
            if (ACTIVE_CALORIES_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                try {
                    caloriesProjector.projectCorrectedCalories(
                            event.deviceId(),
                            correction.correctedPayload(),
                            correction.correctedOccurredAt()
                    );
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("Version conflict while projecting corrected calories, skipping");
                } catch (DataAccessException e) {
                    log.error("Database error while projecting corrected calories: {}", e.getMessage(), e);
                } catch (RuntimeException e) {
                    log.error("Unexpected error while projecting corrected calories: {}", e.getMessage(), e);
                    throw e;
                }
            }
        });

        affectedDates.forEach(date -> {
            try {
                caloriesProjector.reprojectForDate(event.deviceId(), date);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Version conflict while reprojecting calories for date {}, skipping", date);
            } catch (DataAccessException e) {
                log.error("Database error while reprojecting calories for date {}: {}", date, e.getMessage(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error while reprojecting calories for date {}: {}", date, e.getMessage(), e);
                throw e;
            }
        });
    }
}
