package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummary.Activity;
import com.healthassistant.heartrate.api.HeartRateFacade;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.EventData;
import com.healthassistant.healthevents.api.dto.payload.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final HeartRateFacade heartRateFacade;

    DailySummary aggregate(String deviceId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(POLAND_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        List<EventData> events = healthEventsFacade.findEventsForDateRange(deviceId, dayStart, dayEnd)
                .stream()
                .map(stored -> new EventData(
                        stored.eventType().value(),
                        stored.occurredAt(),
                        stored.payload(),
                        stored.deviceId().value(),
                        stored.idempotencyKey().value()
                ))
                .toList();

        log.info("Aggregating {} events for device {} date {}", events.size(), maskDeviceId(deviceId), date);

        Activity activity = aggregateActivity(events);
        List<Exercise> exercises = aggregateExercises(events);
        List<Workout> workouts = aggregateWorkouts(events);
        List<Sleep> sleep = aggregateSleep(events);
        Heart heart = aggregateHeart(events, deviceId, date);
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
        var accumulated = events.stream()
                .map(EventData::payload)
                .reduce(
                        new ActivityAccumulator(0, 0, 0, 0L),
                        (acc, payload) -> switch (payload) {
                            case StepsPayload steps -> acc.addSteps(orZero(steps.count()));
                            case ActiveMinutesPayload am -> acc.addActiveMinutes(orZero(am.activeMinutes()));
                            case ActiveCaloriesPayload ac -> acc.addActiveCalories(orZeroInt(ac.energyKcal()));
                            case DistanceBucketPayload dist -> acc.addDistance(orZeroLong(dist.distanceMeters()));
                            default -> acc;
                        },
                        ActivityAccumulator::merge
                );

        return new Activity(
                nullIfZero(accumulated.steps()),
                nullIfZero(accumulated.activeMinutes()),
                nullIfZero(accumulated.activeCalories()),
                nullIfZero(accumulated.distanceMeters())
        );
    }

    private record ActivityAccumulator(int steps, int activeMinutes, int activeCalories, long distanceMeters) {
        ActivityAccumulator addSteps(int value) {
            return new ActivityAccumulator(steps + value, activeMinutes, activeCalories, distanceMeters);
        }

        ActivityAccumulator addActiveMinutes(int value) {
            return new ActivityAccumulator(steps, activeMinutes + value, activeCalories, distanceMeters);
        }

        ActivityAccumulator addActiveCalories(int value) {
            return new ActivityAccumulator(steps, activeMinutes, activeCalories + value, distanceMeters);
        }

        ActivityAccumulator addDistance(long value) {
            return new ActivityAccumulator(steps, activeMinutes, activeCalories, distanceMeters + value);
        }

        ActivityAccumulator merge(ActivityAccumulator other) {
            return new ActivityAccumulator(
                    steps + other.steps,
                    activeMinutes + other.activeMinutes,
                    activeCalories + other.activeCalories,
                    distanceMeters + other.distanceMeters
            );
        }
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static int orZeroInt(Double value) {
        return value != null ? value.intValue() : 0;
    }

    private static long orZeroLong(Double value) {
        return value != null ? Math.round(value) : 0L;
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

            if (start == null || end == null) {
                return null;
            }

            return new Exercise(
                    "WALK",
                    start,
                    end,
                    walking.durationMinutes(),
                    walking.totalDistanceMeters(),
                    walking.totalSteps(),
                    walking.avgHeartRate(),
                    walking.totalCalories()
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
            return new Workout(workout.workoutId(), workout.note(), workout.performedAt());
        } catch (Exception e) {
            log.warn("Failed to convert event to workout: {}", e.getMessage());
            return null;
        }
    }

    private List<Sleep> aggregateSleep(List<EventData> events) {
        List<SleepSessionPayload> sessions = events.stream()
                .filter(e -> e.payload() instanceof SleepSessionPayload)
                .collect(java.util.stream.Collectors.toMap(
                        e -> ((SleepSessionPayload) e.payload()).sleepStart(),
                        e -> (SleepSessionPayload) e.payload(),
                        (older, newer) -> newer,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        return removeShorterOverlappingSleepSessions(sessions).stream()
                .map(s -> new Sleep(s.sleepStart(), s.sleepEnd(), s.totalMinutes()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<SleepSessionPayload> removeShorterOverlappingSleepSessions(List<SleepSessionPayload> sessions) {
        return java.util.stream.IntStream.range(0, sessions.size())
                .filter(i -> java.util.stream.IntStream.range(0, sessions.size())
                        .filter(j -> j != i)
                        .noneMatch(j -> sleepSessionsOverlap(sessions.get(i), sessions.get(j))
                                && isOtherSleepLongerOrHasPriority(sessions.get(j), sessions.get(i), j, i)))
                .mapToObj(sessions::get)
                .toList();
    }

    private boolean isOtherSleepLongerOrHasPriority(SleepSessionPayload other, SleepSessionPayload candidate,
                                                     int otherIndex, int candidateIndex) {
        int otherMinutes = other.totalMinutes() != null ? other.totalMinutes() : 0;
        int candidateMinutes = candidate.totalMinutes() != null ? candidate.totalMinutes() : 0;
        if (otherMinutes != candidateMinutes) {
            return otherMinutes > candidateMinutes;
        }
        return otherIndex > candidateIndex;
    }

    private boolean sleepSessionsOverlap(SleepSessionPayload a, SleepSessionPayload b) {
        if (a.sleepStart() == null || a.sleepEnd() == null
                || b.sleepStart() == null || b.sleepEnd() == null) {
            return false;
        }
        return a.sleepStart().isBefore(b.sleepEnd()) && a.sleepEnd().isAfter(b.sleepStart());
    }

    private Heart aggregateHeart(List<EventData> events, String deviceId, LocalDate date) {
        var accumulated = events.stream()
                .map(EventData::payload)
                .reduce(
                        new HeartAccumulator(List.of(), null),
                        (acc, payload) -> switch (payload) {
                            case HeartRatePayload hr -> acc.addHeartRate(
                                    hr.avg() != null ? hr.avg().intValue() : null,
                                    hr.max()
                            );
                            case WalkingSessionPayload walking -> acc.addHeartRate(
                                    walking.avgHeartRate(),
                                    walking.maxHeartRate()
                            );
                            default -> acc;
                        },
                        HeartAccumulator::merge
                );

        List<Integer> heartRates = accumulated.avgRates();
        Integer avgBpm = heartRates.isEmpty() ? null :
                (int) heartRates.stream().mapToInt(Integer::intValue).average().orElse(0.0);

        Integer restingBpm = heartRateFacade.getRestingBpmForDate(deviceId, date).orElse(null);

        return new Heart(restingBpm, avgBpm, accumulated.maxHr());
    }

    private record HeartAccumulator(List<Integer> avgRates, Integer maxHr) {
        HeartAccumulator addHeartRate(Integer avg, Integer max) {
            List<Integer> newRates = avg != null
                    ? java.util.stream.Stream.concat(avgRates.stream(), java.util.stream.Stream.of(avg)).toList()
                    : avgRates;
            Integer newMax = max != null && (maxHr == null || max > maxHr) ? max : maxHr;
            return new HeartAccumulator(newRates, newMax);
        }

        HeartAccumulator merge(HeartAccumulator other) {
            List<Integer> mergedRates = java.util.stream.Stream.concat(avgRates.stream(), other.avgRates.stream()).toList();
            Integer mergedMax = Optional.ofNullable(maxHr)
                    .map(m -> Optional.ofNullable(other.maxHr).map(o -> Math.max(m, o)).orElse(m))
                    .orElse(other.maxHr);
            return new HeartAccumulator(mergedRates, mergedMax);
        }
    }

    private Nutrition aggregateNutrition(List<EventData> events) {
        var accumulated = events.stream()
                .map(EventData::payload)
                .filter(MealRecordedPayload.class::isInstance)
                .map(MealRecordedPayload.class::cast)
                .reduce(
                        new NutritionAccumulator(0, 0, 0, 0, 0),
                        (acc, meal) -> new NutritionAccumulator(
                                acc.calories() + orZero(meal.caloriesKcal()),
                                acc.protein() + orZero(meal.proteinGrams()),
                                acc.fat() + orZero(meal.fatGrams()),
                                acc.carbs() + orZero(meal.carbohydratesGrams()),
                                acc.mealCount() + 1
                        ),
                        NutritionAccumulator::merge
                );

        return new Nutrition(
                nullIfZero(accumulated.calories()),
                nullIfZero(accumulated.protein()),
                nullIfZero(accumulated.fat()),
                nullIfZero(accumulated.carbs()),
                nullIfZero(accumulated.mealCount())
        );
    }

    private record NutritionAccumulator(int calories, int protein, int fat, int carbs, int mealCount) {
        NutritionAccumulator merge(NutritionAccumulator other) {
            return new NutritionAccumulator(
                    calories + other.calories,
                    protein + other.protein,
                    fat + other.fat,
                    carbs + other.carbs,
                    mealCount + other.mealCount
            );
        }
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

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
