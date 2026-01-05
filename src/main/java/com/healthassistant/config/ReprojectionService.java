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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
class ReprojectionService {

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

    @Transactional(timeout = 3600)
    public ReprojectionResult reprojectAll() {
        log.info("Starting full reprojection of all events");

        deleteAllProjections();

        long totalEvents = healthEventsFacade.countAllEvents();
        log.info("Total events to reproject: {}", totalEvents);

        if (totalEvents == 0) {
            return new ReprojectionResult(0, 0, 0, 0, 0, 0, 0);
        }

        Set<LocalDate> allAffectedDates = new HashSet<>();
        Set<String> allEventTypes = new HashSet<>();
        AtomicReference<String> deviceIdRef = new AtomicReference<>();

        AtomicInteger stepsCount = new AtomicInteger();
        AtomicInteger workoutsCount = new AtomicInteger();
        AtomicInteger sleepCount = new AtomicInteger();
        AtomicInteger activityCount = new AtomicInteger();
        AtomicInteger caloriesCount = new AtomicInteger();
        AtomicInteger mealsCount = new AtomicInteger();
        AtomicInteger processedTotal = new AtomicInteger();

        int totalBatches = (int) Math.ceil((double) totalEvents / BATCH_SIZE);

        IntStream.range(0, totalBatches)
                .takeWhile(page -> processedTotal.get() < totalEvents)
                .forEach(page -> {
                    log.info("Processing batch {} (offset: {}, batch size: {})", page + 1, processedTotal.get(), BATCH_SIZE);

                    List<StoredEventData> batch = healthEventsFacade.findEventsForReprojection(page, BATCH_SIZE);

                    if (batch.isEmpty()) {
                        return;
                    }

                    deviceIdRef.compareAndSet(null, batch.getFirst().deviceId().value());

                    Map<String, List<StoredEventData>> eventsByType = batch.stream()
                            .collect(Collectors.groupingBy(e -> e.eventType().value()));

                    stepsCount.addAndGet(processBatchForType(eventsByType, STEPS_BUCKETED_V1, stepsFacade::projectEvents));
                    workoutsCount.addAndGet(processBatchForType(eventsByType, WORKOUT_V1, workoutFacade::projectEvents));
                    sleepCount.addAndGet(processBatchForType(eventsByType, SLEEP_SESSION_V1, sleepFacade::projectEvents));
                    activityCount.addAndGet(processBatchForType(eventsByType, ACTIVE_MINUTES_V1, activityFacade::projectEvents));
                    caloriesCount.addAndGet(processBatchForType(eventsByType, ACTIVE_CALORIES_V1, caloriesFacade::projectEvents));
                    mealsCount.addAndGet(processBatchForType(eventsByType, MEAL_V1, mealsFacade::projectEvents));

                    batch.forEach(e -> {
                        allAffectedDates.add(e.occurredAt().atZone(POLAND_ZONE).toLocalDate());
                        allEventTypes.add(e.eventType().value());
                    });

                    processedTotal.addAndGet(batch.size());
                    log.info("Processed {}/{} events", processedTotal.get(), totalEvents);
                });

        String deviceId = deviceIdRef.get();
        if (deviceId != null && !allAffectedDates.isEmpty()) {
            log.info("Publishing AllEventsStoredEvent for {} affected dates", allAffectedDates.size());
            eventPublisher.publishEvent(new AllEventsStoredEvent(deviceId, allAffectedDates, allEventTypes));
        }

        log.info("Reprojection completed: steps={}, workouts={}, sleep={}, activity={}, calories={}, meals={}",
                stepsCount.get(), workoutsCount.get(), sleepCount.get(), activityCount.get(), caloriesCount.get(), mealsCount.get());

        return new ReprojectionResult(
                processedTotal.get(),
                stepsCount.get(),
                workoutsCount.get(),
                sleepCount.get(),
                activityCount.get(),
                caloriesCount.get(),
                mealsCount.get()
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

    private int processBatchForType(
            Map<String, List<StoredEventData>> eventsByType,
            String eventType,
            Consumer<List<StoredEventData>> projector
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
