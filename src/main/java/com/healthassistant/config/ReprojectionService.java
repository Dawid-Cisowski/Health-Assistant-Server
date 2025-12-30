package com.healthassistant.config;

import com.healthassistant.activity.api.ActivityFacade;
import com.healthassistant.calories.api.CaloriesFacade;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.AllEventsStoredEvent;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.workout.api.WorkoutFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReprojectionService {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final int BATCH_SIZE = 500;

    private static final String STEPS_BUCKETED_V1 = "StepsBucketedRecorded.v1";
    private static final String WORKOUT_V1 = "WorkoutRecorded.v1";
    private static final String SLEEP_SESSION_V1 = "SleepSessionRecorded.v1";
    private static final String ACTIVE_MINUTES_V1 = "ActiveMinutesRecorded.v1";
    private static final String ACTIVE_CALORIES_V1 = "ActiveCaloriesBurnedRecorded.v1";
    private static final String MEAL_V1 = "MealRecorded.v1";

    private final HealthEventsFacade healthEventsFacade;
    private final StepsFacade stepsFacade;
    private final WorkoutFacade workoutFacade;
    private final SleepFacade sleepFacade;
    private final CaloriesFacade caloriesFacade;
    private final ActivityFacade activityFacade;
    private final MealsFacade mealsFacade;
    private final DailySummaryFacade dailySummaryFacade;
    private final ApplicationEventPublisher eventPublisher;

    public ReprojectionResult reprojectAll() {
        log.info("Starting full reprojection of all events");

        // 1. Delete all existing projections (in separate transaction)
        deleteAllProjections();

        // 2. Count total events
        long totalEvents = healthEventsFacade.countAllEvents();
        log.info("Total events to reproject: {}", totalEvents);

        if (totalEvents == 0) {
            return new ReprojectionResult(0, 0, 0, 0, 0, 0, 0);
        }

        // 3. Process in batches
        int stepsCount = 0;
        int workoutsCount = 0;
        int sleepCount = 0;
        int activityCount = 0;
        int caloriesCount = 0;
        int mealsCount = 0;

        Set<LocalDate> allAffectedDates = new HashSet<>();
        Set<String> allEventTypes = new HashSet<>();
        String deviceId = null;

        int page = 0;
        int processedTotal = 0;

        while (processedTotal < totalEvents) {
            log.info("Processing batch {} (offset: {}, batch size: {})", page + 1, processedTotal, BATCH_SIZE);

            List<StoredEventData> batch = healthEventsFacade.findEventsForReprojection(page, BATCH_SIZE);

            if (batch.isEmpty()) {
                break;
            }

            if (deviceId == null && !batch.isEmpty()) {
                deviceId = batch.getFirst().deviceId().value();
            }

            // Group by type
            Map<String, List<StoredEventData>> eventsByType = batch.stream()
                    .collect(Collectors.groupingBy(e -> e.eventType().value()));

            // Process each type - call projectors directly via facades
            stepsCount += processBatchForType(eventsByType, STEPS_BUCKETED_V1, stepsFacade::projectEvents);
            workoutsCount += processBatchForType(eventsByType, WORKOUT_V1, workoutFacade::projectEvents);
            sleepCount += processBatchForType(eventsByType, SLEEP_SESSION_V1, sleepFacade::projectEvents);
            activityCount += processBatchForType(eventsByType, ACTIVE_MINUTES_V1, activityFacade::projectEvents);
            caloriesCount += processBatchForType(eventsByType, ACTIVE_CALORIES_V1, caloriesFacade::projectEvents);
            mealsCount += processBatchForType(eventsByType, MEAL_V1, mealsFacade::projectEvents);

            // Collect dates and types for daily summary
            batch.forEach(e -> {
                allAffectedDates.add(e.occurredAt().atZone(POLAND_ZONE).toLocalDate());
                allEventTypes.add(e.eventType().value());
            });

            processedTotal += batch.size();
            page++;

            log.info("Processed {}/{} events", processedTotal, totalEvents);
        }

        // 4. Trigger daily summary aggregation
        if (deviceId != null && !allAffectedDates.isEmpty()) {
            log.info("Publishing AllEventsStoredEvent for {} affected dates", allAffectedDates.size());
            eventPublisher.publishEvent(new AllEventsStoredEvent(deviceId, allAffectedDates, allEventTypes));
        }

        log.info("Reprojection completed: steps={}, workouts={}, sleep={}, activity={}, calories={}, meals={}",
                stepsCount, workoutsCount, sleepCount, activityCount, caloriesCount, mealsCount);

        return new ReprojectionResult(
                processedTotal,
                stepsCount,
                workoutsCount,
                sleepCount,
                activityCount,
                caloriesCount,
                mealsCount
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAllProjections() {
        log.info("Deleting all existing projections");
        stepsFacade.deleteAllProjections();
        workoutFacade.deleteAllProjections();
        sleepFacade.deleteAllProjections();
        caloriesFacade.deleteAllProjections();
        activityFacade.deleteAllProjections();
        mealsFacade.deleteAllProjections();
        dailySummaryFacade.deleteAllSummaries();
        log.info("All projections deleted");
    }

    @Transactional
    public void reprojectForDate(String deviceId, LocalDate date) {
        log.info("Reprojecting events for device {} date {}", deviceId, date);

        // 1. Delete projections for this date
        stepsFacade.deleteProjectionsForDate(deviceId, date);
        sleepFacade.deleteProjectionsForDate(deviceId, date);
        workoutFacade.deleteProjectionsForDate(deviceId, date);
        caloriesFacade.deleteProjectionsForDate(deviceId, date);
        activityFacade.deleteProjectionsForDate(deviceId, date);
        mealsFacade.deleteProjectionsForDate(deviceId, date);
        dailySummaryFacade.deleteSummaryForDate(deviceId, date);

        // 2. Find events for this date
        ZonedDateTime dayStart = date.atStartOfDay(POLAND_ZONE);
        ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE);
        Instant from = dayStart.toInstant();
        Instant to = dayEnd.toInstant();

        List<StoredEventData> events = healthEventsFacade.findEventsForDateRange(deviceId, from, to);
        log.debug("Found {} events for device {} date {}", events.size(), deviceId, date);

        if (events.isEmpty()) {
            return;
        }

        // 3. Project events
        Map<String, List<StoredEventData>> eventsByType = events.stream()
                .collect(Collectors.groupingBy(e -> e.eventType().value()));

        int stepsCount = processBatchForType(eventsByType, STEPS_BUCKETED_V1, stepsFacade::projectEvents);
        int workoutsCount = processBatchForType(eventsByType, WORKOUT_V1, workoutFacade::projectEvents);
        int sleepCount = processBatchForType(eventsByType, SLEEP_SESSION_V1, sleepFacade::projectEvents);
        int activityCount = processBatchForType(eventsByType, ACTIVE_MINUTES_V1, activityFacade::projectEvents);
        int caloriesCount = processBatchForType(eventsByType, ACTIVE_CALORIES_V1, caloriesFacade::projectEvents);
        int mealsCount = processBatchForType(eventsByType, MEAL_V1, mealsFacade::projectEvents);

        // 4. Generate daily summary
        dailySummaryFacade.generateDailySummary(deviceId, date);

        log.info("Reprojection for date {} completed: steps={}, workouts={}, sleep={}, activity={}, calories={}, meals={}",
                date, stepsCount, workoutsCount, sleepCount, activityCount, caloriesCount, mealsCount);
    }

    private int processBatchForType(
            Map<String, List<StoredEventData>> eventsByType,
            String eventType,
            java.util.function.Consumer<List<StoredEventData>> projector
    ) {
        List<StoredEventData> events = eventsByType.getOrDefault(eventType, List.of());
        if (!events.isEmpty()) {
            projector.accept(events);
        }
        return events.size();
    }

    public record ReprojectionResult(
            int totalEvents,
            int stepsEvents,
            int workoutEvents,
            int sleepEvents,
            int activityEvents,
            int caloriesEvents,
            int mealsEvents
    ) {}
}
