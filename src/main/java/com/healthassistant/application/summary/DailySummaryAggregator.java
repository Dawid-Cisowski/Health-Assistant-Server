package com.healthassistant.application.summary;

import com.healthassistant.domain.summary.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryAggregator {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HealthEventsQuery healthEventsQuery;

    DailySummary aggregate(LocalDate date) {
        Instant dayStart = date.atStartOfDay(POLAND_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        List<HealthEventsQuery.EventData> events = healthEventsQuery.findEventsByDateRange(dayStart, dayEnd);

        log.info("Aggregating {} events for date {}", events.size(), date);

        DailySummary.Activity activity = aggregateActivity(events);
        List<DailySummary.Exercise> exercises = aggregateExercises(events);
        List<DailySummary.Workout> workouts = aggregateWorkouts(events);
        DailySummary.Sleep sleep = aggregateSleep(events);
        DailySummary.Heart heart = aggregateHeart(events);

        return new DailySummary(
                date,
                activity,
                exercises,
                workouts,
                sleep,
                heart
        );
    }

    private DailySummary.Activity aggregateActivity(List<HealthEventsQuery.EventData> events) {
        int totalSteps = 0;
        int totalActiveMinutes = 0;
        int totalActiveCalories = 0;
        long totalDistanceMeters = 0L;

        for (HealthEventsQuery.EventData event : events) {
            Map<String, Object> payload = event.payload();

            totalSteps += extractSteps(event.eventType(), payload);
            totalActiveMinutes += extractActiveMinutes(event.eventType(), payload);
            totalActiveCalories += extractActiveCalories(event.eventType(), payload);
            totalDistanceMeters += extractDistance(event.eventType(), payload);
        }

        return new DailySummary.Activity(
                nullIfZero(totalSteps),
                nullIfZero(totalActiveMinutes),
                nullIfZero(totalActiveCalories),
                nullIfZero(totalDistanceMeters)
        );
    }

    private int extractSteps(String eventType, Map<String, Object> payload) {
        if ("StepsBucketedRecorded.v1".equals(eventType)) {
            return getInteger(payload, "count", 0);
        }
        if ("WalkingSessionRecorded.v1".equals(eventType)) {
            return getInteger(payload, "totalSteps", 0);
        }
        return 0;
    }

    private int extractActiveMinutes(String eventType, Map<String, Object> payload) {
        if (!"ActiveMinutesRecorded.v1".equals(eventType)) return 0;
        return getInteger(payload, "activeMinutes", 0);
    }

    private int extractActiveCalories(String eventType, Map<String, Object> payload) {
        if ("ActiveCaloriesBurnedRecorded.v1".equals(eventType)) {
            return getInteger(payload, "energyKcal", 0);
        }
        if ("WalkingSessionRecorded.v1".equals(eventType)) {
            return getInteger(payload, "totalCalories", 0);
        }
        return 0;
    }

    private long extractDistance(String eventType, Map<String, Object> payload) {
        if ("DistanceBucketRecorded.v1".equals(eventType)) {
            Double distance = getDouble(payload, "distanceMeters");
            return distance != null ? Math.round(distance) : 0L;
        }
        if ("WalkingSessionRecorded.v1".equals(eventType)) {
            Double distance = getDouble(payload, "totalDistanceMeters");
            return distance != null ? Math.round(distance) : 0L;
        }
        return 0L;
    }

    private <T extends Number> T nullIfZero(T value) {
        if (value == null) return null;
        if (value instanceof Long) {
            return value.longValue() == 0L ? null : value;
        }
        return value.doubleValue() == 0.0 ? null : value;
    }

    private List<DailySummary.Exercise> aggregateExercises(List<HealthEventsQuery.EventData> events) {
        return events.stream()
                .filter(e -> "WalkingSessionRecorded.v1".equals(e.eventType()))
                .map(this::toExercise)
                .filter(Objects::nonNull)
                .toList();
    }

    private DailySummary.Exercise toExercise(HealthEventsQuery.EventData event) {
        Map<String, Object> payload = event.payload();

        try {
            Instant start = parseInstant(payload.get("start"));
            Instant end = parseInstant(payload.get("end"));
            Integer durationMinutes = getInteger(payload, "durationMinutes");
            Double distanceMetersDouble = getDouble(payload, "totalDistanceMeters");
            Long distanceMeters = distanceMetersDouble != null ? Math.round(distanceMetersDouble) : null;
            Integer steps = getInteger(payload, "totalSteps");
            Integer avgHr = getInteger(payload, "avgHeartRate");
            Integer energyKcal = getInteger(payload, "totalCalories");
            if (energyKcal == null) {
                energyKcal = calculateWorkoutCalories(payload, durationMinutes);
            }

            if (start == null || end == null) {
                return null;
            }

            return new DailySummary.Exercise(
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

    private List<DailySummary.Workout> aggregateWorkouts(List<HealthEventsQuery.EventData> events) {
        return events.stream()
                .filter(e -> "WorkoutRecorded.v1".equals(e.eventType()))
                .map(this::toWorkout)
                .filter(Objects::nonNull)
                .toList();
    }

    private DailySummary.Workout toWorkout(HealthEventsQuery.EventData event) {
        Map<String, Object> payload = event.payload();

        try {
            String workoutId = getString(payload, "workoutId");
            String note = getString(payload, "note");

            return new DailySummary.Workout(workoutId, note);
        } catch (Exception e) {
            log.warn("Failed to convert event to workout: {}", e.getMessage());
            return null;
        }
    }

    private DailySummary.Sleep aggregateSleep(List<HealthEventsQuery.EventData> events) {
        return events.stream()
                .filter(e -> "SleepSessionRecorded.v1".equals(e.eventType()))
                .findFirst()
                .map(this::toSleep)
                .orElse(new DailySummary.Sleep(null, null, null));
    }

    private DailySummary.Sleep toSleep(HealthEventsQuery.EventData event) {
        Map<String, Object> payload = event.payload();

        Instant sleepStart = parseInstant(payload.get("sleepStart"));
        Instant sleepEnd = parseInstant(payload.get("sleepEnd"));
        Integer totalMinutes = getInteger(payload, "totalMinutes");

        return new DailySummary.Sleep(sleepStart, sleepEnd, totalMinutes);
    }

    private DailySummary.Heart aggregateHeart(List<HealthEventsQuery.EventData> events) {
        List<Integer> heartRates = new ArrayList<>();
        Integer maxHr = null;

        for (HealthEventsQuery.EventData event : events) {
            Map<String, Object> payload = event.payload();

            if ("HeartRateSummaryRecorded.v1".equals(event.eventType())) {
                Integer avg = getInteger(payload, "avg");
                Integer max = getInteger(payload, "max");

                if (avg != null) {
                    heartRates.add(avg);
                }
                if (max != null && (maxHr == null || max > maxHr)) {
                    maxHr = max;
                }
            } else if ("WalkingSessionRecorded.v1".equals(event.eventType())) {
                Integer avg = getInteger(payload, "avgHeartRate");
                Integer max = getInteger(payload, "maxHeartRate");
                
                if (avg != null) {
                    heartRates.add(avg);
                }
                if (max != null && (maxHr == null || max > maxHr)) {
                    maxHr = max;
                }
            }
        }

        Integer avgBpm = heartRates.isEmpty() ? null :
                (int) heartRates.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        Integer restingBpm = heartRates.isEmpty() ? null :
                heartRates.stream().mapToInt(Integer::intValue).min().orElse(0);

        return new DailySummary.Heart(restingBpm, avgBpm, maxHr);
    }


    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        return getInteger(map, key, null);
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Integer calculateWorkoutCalories(Map<String, Object> payload, Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes == 0) return null;

        Integer avgHr = getInteger(payload, "avgHeartRate");
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

