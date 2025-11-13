package com.healthassistant.application.summary;

import com.healthassistant.domain.summary.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryAggregator {

    private final HealthEventsQuery healthEventsQuery;

    DailySummary aggregate(LocalDate date) {
        Instant dayStart = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<HealthEventsQuery.EventData> events = healthEventsQuery.findEventsByDateRange(dayStart, dayEnd);

        log.info("Aggregating {} events for date {}", events.size(), date);

        DailySummary.Activity activity = aggregateActivity(events);
        List<DailySummary.Workout> workouts = aggregateWorkouts(events);
        DailySummary.Sleep sleep = aggregateSleep(events);
        DailySummary.Heart heart = aggregateHeart(events);
        DailySummary.Score score = calculateScores(activity, sleep, heart);

        return new DailySummary(
                date,
                activity,
                workouts,
                sleep,
                heart,
                score
        );
    }

    private DailySummary.Activity aggregateActivity(List<HealthEventsQuery.EventData> events) {
        int totalSteps = 0;
        int totalActiveMinutes = 0;
        int totalActiveCalories = 0;
        double totalDistanceMeters = 0.0;

        for (HealthEventsQuery.EventData event : events) {
            Map<String, Object> payload = event.payload();

            switch (event.eventType()) {
                case "StepsBucketedRecorded.v1" -> {
                    Object count = payload.get("count");
                    if (count instanceof Number) {
                        totalSteps += ((Number) count).intValue();
                    }
                }
                case "ActiveMinutesRecorded.v1" -> {
                    Object minutes = payload.get("activeMinutes");
                    if (minutes instanceof Number) {
                        totalActiveMinutes += ((Number) minutes).intValue();
                    }
                }
                case "ActiveCaloriesBurnedRecorded.v1" -> {
                    Object calories = payload.get("energyKcal");
                    if (calories instanceof Number) {
                        totalActiveCalories += ((Number) calories).intValue();
                    }
                }
            }
        }

        for (HealthEventsQuery.EventData event : events) {
            if ("ExerciseSessionRecorded.v1".equals(event.eventType())) {
                Map<String, Object> payload = event.payload();
                Object distance = payload.get("distanceMeters");
                if (distance != null) {
                    try {
                        if (distance instanceof String) {
                            totalDistanceMeters += Double.parseDouble((String) distance);
                        } else if (distance instanceof Number) {
                            totalDistanceMeters += ((Number) distance).doubleValue();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse distanceMeters: {}", distance);
                    }
                }
            }
        }

        return new DailySummary.Activity(
                totalSteps > 0 ? totalSteps : null,
                totalActiveMinutes > 0 ? totalActiveMinutes : null,
                totalActiveCalories > 0 ? totalActiveCalories : null,
                totalDistanceMeters > 0 ? totalDistanceMeters : null
        );
    }

    private List<DailySummary.Workout> aggregateWorkouts(List<HealthEventsQuery.EventData> events) {
        return events.stream()
                .filter(e -> "ExerciseSessionRecorded.v1".equals(e.eventType()))
                .map(this::toWorkout)
                .filter(Objects::nonNull)
                .toList();
    }

    private DailySummary.Workout toWorkout(HealthEventsQuery.EventData event) {
        Map<String, Object> payload = event.payload();

        try {
            String type = getString(payload, "type");
            if ("other_0".equals(type)) {
                type = "WALK";
            }
            Instant start = parseInstant(payload.get("start"));
            Instant end = parseInstant(payload.get("end"));
            Integer durationMinutes = getInteger(payload, "durationMinutes");
            Double distanceMeters = getDouble(payload, "distanceMeters");
            Integer avgHr = getInteger(payload, "avgHr");
            Integer energyKcal = calculateWorkoutCalories(payload, durationMinutes);

            if (start == null || end == null) {
                return null;
            }

            return new DailySummary.Workout(
                    type,
                    start,
                    end,
                    durationMinutes,
                    distanceMeters,
                    avgHr,
                    energyKcal
            );
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
            } else if ("ExerciseSessionRecorded.v1".equals(event.eventType())) {
                Integer max = getInteger(payload, "maxHr");
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

    private DailySummary.Score calculateScores(
            DailySummary.Activity activity,
            DailySummary.Sleep sleep,
            DailySummary.Heart heart
    ) {
        int activityScore = calculateActivityScore(activity);
        int sleepScore = calculateSleepScore(sleep);
        int readinessScore = calculateReadinessScore(heart);
        int overallScore = (activityScore + sleepScore + readinessScore) / 3;

        return new DailySummary.Score(activityScore, sleepScore, readinessScore, overallScore);
    }

    private int calculateActivityScore(DailySummary.Activity activity) {
        if (activity == null) return 0;

        int score = 0;
        if (activity.steps() != null && activity.steps() >= 10000) score += 40;
        else if (activity.steps() != null && activity.steps() >= 5000) score += 20;

        if (activity.activeMinutes() != null && activity.activeMinutes() >= 30) score += 30;
        else if (activity.activeMinutes() != null && activity.activeMinutes() >= 15) score += 15;

        if (activity.activeCalories() != null && activity.activeCalories() >= 400) score += 30;
        else if (activity.activeCalories() != null && activity.activeCalories() >= 200) score += 15;

        return Math.min(score, 100);
    }

    private int calculateSleepScore(DailySummary.Sleep sleep) {
        if (sleep == null || sleep.totalMinutes() == null) return 0;

        int minutes = sleep.totalMinutes();
        if (minutes >= 420 && minutes <= 540) return 100;
        if (minutes >= 360 && minutes < 420) return 80;
        if (minutes > 540 && minutes <= 600) return 80;
        if (minutes >= 300 && minutes < 360) return 60;
        if (minutes > 600 && minutes <= 660) return 60;
        return 40;
    }

    private int calculateReadinessScore(DailySummary.Heart heart) {
        if (heart == null || heart.restingBpm() == null) return 50;

        int resting = heart.restingBpm();
        if (resting >= 60 && resting <= 70) return 100;
        if (resting >= 50 && resting < 60) return 90;
        if (resting > 70 && resting <= 80) return 80;
        if (resting >= 40 && resting < 50) return 70;
        if (resting > 80 && resting <= 90) return 60;
        return 50;
    }


    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
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

        Integer avgHr = getInteger(payload, "avgHr");
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

