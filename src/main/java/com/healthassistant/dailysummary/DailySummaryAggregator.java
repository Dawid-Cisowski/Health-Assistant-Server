package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummary.Activity;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.EventData;
import com.healthassistant.healthevents.api.dto.payload.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.healthassistant.dailysummary.api.dto.DailySummary.Exercise;
import static com.healthassistant.dailysummary.api.dto.DailySummary.Heart;
import static com.healthassistant.dailysummary.api.dto.DailySummary.Meal;
import static com.healthassistant.dailysummary.api.dto.DailySummary.Nutrition;
import static com.healthassistant.dailysummary.api.dto.DailySummary.Sleep;
import static com.healthassistant.dailysummary.api.dto.DailySummary.Workout;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryAggregator {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HealthEventsFacade healthEventsFacade;

    DailySummary aggregate(LocalDate date) {
        Instant dayStart = date.atStartOfDay(POLAND_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        List<EventData> events = healthEventsFacade.findEventsByOccurredAtBetween(dayStart, dayEnd);

        log.info("Aggregating {} events for date {}", events.size(), date);

        Activity activity = aggregateActivity(events);
        List<Exercise> exercises = aggregateExercises(events);
        List<Workout> workouts = aggregateWorkouts(events);
        List<Sleep> sleep = aggregateSleep(events);
        Heart heart = aggregateHeart(events);
        Nutrition nutrition = aggregateNutrition(events);
        List<Meal> meals = aggregateMeals(events);

        return new DailySummary(
                date,
                activity,
                exercises,
                workouts,
                sleep,
                heart,
                nutrition,
                meals
        );
    }

    private Activity aggregateActivity(List<EventData> events) {
        int totalSteps = 0;
        int totalActiveMinutes = 0;
        int totalActiveCalories = 0;
        long totalDistanceMeters = 0L;

        for (EventData event : events) {
            EventPayload payload = event.payload();

            switch (payload) {
                case StepsPayload steps -> totalSteps += steps.count() != null ? steps.count() : 0;
                case ActiveMinutesPayload am -> totalActiveMinutes += am.activeMinutes() != null ? am.activeMinutes() : 0;
                case ActiveCaloriesPayload ac -> totalActiveCalories += ac.energyKcal() != null ? ac.energyKcal().intValue() : 0;
                case DistanceBucketPayload dist -> totalDistanceMeters += dist.distanceMeters() != null ? Math.round(dist.distanceMeters()) : 0L;
                default -> { }
            }
        }

        return new Activity(
                nullIfZero(totalSteps),
                nullIfZero(totalActiveMinutes),
                nullIfZero(totalActiveCalories),
                nullIfZero(totalDistanceMeters)
        );
    }

    private <T extends Number> T nullIfZero(T value) {
        if (value == null) return null;
        if (value instanceof Long) {
            return value.longValue() == 0L ? null : value;
        }
        return value.doubleValue() == 0.0 ? null : value;
    }

    private List<Exercise> aggregateExercises(List<EventData> events) {
        return events.stream()
                .filter(e -> e.payload() instanceof WalkingSessionPayload)
                .map(this::toExercise)
                .filter(Objects::nonNull)
                .toList();
    }

    private Exercise toExercise(EventData event) {
        if (!(event.payload() instanceof WalkingSessionPayload walking)) {
            return null;
        }

        try {
            Instant start = walking.start();
            Instant end = walking.end();
            Integer durationMinutes = walking.durationMinutes();
            Long distanceMeters = walking.totalDistanceMeters();
            Integer steps = walking.totalSteps();
            Integer avgHr = walking.avgHeartRate();
            Integer energyKcal = walking.totalCalories();
            if (energyKcal == null && avgHr != null && durationMinutes != null) {
                energyKcal = calculateWorkoutCalories(avgHr, durationMinutes);
            }

            if (start == null || end == null) {
                return null;
            }

            return new Exercise(
                    "WALK",
                    start,
                    end,
                    durationMinutes,
                    distanceMeters,
                    steps,
                    avgHr,
                    energyKcal
            );
        } catch (Exception e) {
            log.warn("Failed to convert event to workout: {}", e.getMessage());
            return null;
        }
    }

    private List<Workout> aggregateWorkouts(List<EventData> events) {
        return events.stream()
                .filter(e -> e.payload() instanceof WorkoutPayload)
                .map(this::toWorkout)
                .filter(Objects::nonNull)
                .toList();
    }

    private Workout toWorkout(EventData event) {
        if (!(event.payload() instanceof WorkoutPayload workout)) {
            return null;
        }

        try {
            return new Workout(workout.workoutId(), workout.note());
        } catch (Exception e) {
            log.warn("Failed to convert event to workout: {}", e.getMessage());
            return null;
        }
    }

    private List<Sleep> aggregateSleep(List<EventData> events) {
        return events.stream()
                .filter(e -> e.payload() instanceof SleepSessionPayload)
                .map(this::toSleep)
                .filter(Objects::nonNull)
                .toList();
    }

    private Sleep toSleep(EventData event) {
        if (!(event.payload() instanceof SleepSessionPayload sleep)) {
            return null;
        }

        return new Sleep(sleep.sleepStart(), sleep.sleepEnd(), sleep.totalMinutes());
    }

    private Heart aggregateHeart(List<EventData> events) {
        List<Integer> heartRates = new ArrayList<>();
        Integer maxHr = null;

        for (EventData event : events) {
            EventPayload payload = event.payload();

            switch (payload) {
                case HeartRatePayload hr -> {
                    Integer avg = hr.avg() != null ? hr.avg().intValue() : null;
                    Integer max = hr.max();
                    if (avg != null) {
                        heartRates.add(avg);
                    }
                    if (max != null && (maxHr == null || max > maxHr)) {
                        maxHr = max;
                    }
                }
                case WalkingSessionPayload walking -> {
                    Integer avg = walking.avgHeartRate();
                    Integer max = walking.maxHeartRate();
                    if (avg != null) {
                        heartRates.add(avg);
                    }
                    if (max != null && (maxHr == null || max > maxHr)) {
                        maxHr = max;
                    }
                }
                default -> { }
            }
        }

        Integer avgBpm = heartRates.isEmpty() ? null :
                (int) heartRates.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        Integer restingBpm = heartRates.isEmpty() ? null :
                heartRates.stream().mapToInt(Integer::intValue).min().orElse(0);

        return new Heart(restingBpm, avgBpm, maxHr);
    }

    private Nutrition aggregateNutrition(List<EventData> events) {
        int totalCalories = 0;
        int totalProtein = 0;
        int totalFat = 0;
        int totalCarbs = 0;
        int mealCount = 0;

        for (EventData event : events) {
            if (event.payload() instanceof MealRecordedPayload meal) {
                totalCalories += meal.caloriesKcal() != null ? meal.caloriesKcal() : 0;
                totalProtein += meal.proteinGrams() != null ? meal.proteinGrams() : 0;
                totalFat += meal.fatGrams() != null ? meal.fatGrams() : 0;
                totalCarbs += meal.carbohydratesGrams() != null ? meal.carbohydratesGrams() : 0;
                mealCount++;
            }
        }

        return new Nutrition(
                nullIfZero(totalCalories),
                nullIfZero(totalProtein),
                nullIfZero(totalFat),
                nullIfZero(totalCarbs),
                nullIfZero(mealCount)
        );
    }

    private List<Meal> aggregateMeals(List<EventData> events) {
        return events.stream()
                .filter(e -> e.payload() instanceof MealRecordedPayload)
                .map(this::toMeal)
                .filter(Objects::nonNull)
                .toList();
    }

    private Meal toMeal(EventData event) {
        if (!(event.payload() instanceof MealRecordedPayload meal)) {
            return null;
        }

        try {
            return new Meal(
                    meal.title(),
                    meal.mealType() != null ? meal.mealType().name() : null,
                    meal.caloriesKcal(),
                    meal.proteinGrams(),
                    meal.fatGrams(),
                    meal.carbohydratesGrams(),
                    meal.healthRating() != null ? meal.healthRating().name() : null,
                    event.occurredAt()
            );
        } catch (Exception e) {
            log.warn("Failed to convert event to meal: {}", e.getMessage());
            return null;
        }
    }

    private Integer calculateWorkoutCalories(Integer avgHr, Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes == 0) return null;
        if (avgHr == null) return null;

        double mets = estimateMets(avgHr);
        double weightKg = 70.0;
        double caloriesPerMinute = (mets * weightKg * 3.5) / 200.0;

        return (int) Math.round(caloriesPerMinute * durationMinutes);
    }

    private double estimateMets(int heartRate) {
        if (heartRate < 100) return 3.0;
        if (heartRate < 120) return 5.0;
        if (heartRate < 140) return 7.0;
        if (heartRate < 160) return 9.0;
        return 11.0;
    }
}
