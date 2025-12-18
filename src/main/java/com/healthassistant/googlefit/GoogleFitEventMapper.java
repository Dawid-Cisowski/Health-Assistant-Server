package com.healthassistant.googlefit;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.payload.*;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
class GoogleFitEventMapper {

    private static final String GOOGLE_FIT_ORIGIN = "google-fit";
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String DEFAULT_USER_ID = "default";

    List<StoreHealthEventsCommand.EventEnvelope> mapToEventEnvelopes(List<GoogleFitBucketData> buckets) {
        return buckets.stream()
                .flatMap(this::mapBucketToEnvelopes)
                .toList();
    }

    private java.util.stream.Stream<StoreHealthEventsCommand.EventEnvelope> mapBucketToEnvelopes(GoogleFitBucketData bucket) {
        List<StoreHealthEventsCommand.EventEnvelope> envelopes = new ArrayList<>();

        if (bucket.steps() != null && bucket.steps() > 0) {
            envelopes.add(createStepsEnvelope(bucket));
        }

        if (bucket.distance() != null && bucket.distance() > 0) {
            envelopes.add(createDistanceEnvelope(bucket));
        }

        if (bucket.calories() != null && bucket.calories() > 0) {
            envelopes.add(createCaloriesEnvelope(bucket));
        }

        if (bucket.heartRates() != null && !bucket.heartRates().isEmpty()) {
            envelopes.add(createHeartRateEnvelope(bucket));
        }

        Integer steps = bucket.steps() != null ? bucket.steps().intValue() : null;
        Double distanceMeters = bucket.distance();
        if (isActiveMinute(steps, distanceMeters)) {
            envelopes.add(createActiveMinutesEnvelope(bucket));
        }

        return envelopes.stream();
    }

    List<StoreHealthEventsCommand.EventEnvelope> mapSleepSessionsToEnvelopes(List<GoogleFitSession> sessions) {
        return sessions.stream()
                .map(this::createSleepSessionEnvelope)
                .toList();
    }

    private StoreHealthEventsCommand.EventEnvelope createStepsEnvelope(GoogleFitBucketData bucket) {
        Instant bucketStart = bucket.bucketStart();
        Instant bucketEnd = bucket.bucketEnd();

        StepsPayload payload = new StepsPayload(
                bucketStart,
                bucketEnd,
                bucket.steps().intValue(),
                GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|steps|%d|%d",
                bucketStart.toEpochMilli(),
                bucketEnd.toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "StepsBucketedRecorded.v1",
                bucketEnd,
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createDistanceEnvelope(GoogleFitBucketData bucket) {
        Instant bucketStart = bucket.bucketStart();
        Instant bucketEnd = bucket.bucketEnd();

        DistanceBucketPayload payload = new DistanceBucketPayload(
                bucketStart,
                bucketEnd,
                (double) Math.round(bucket.distance()),
                GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|distance|%d|%d",
                bucketStart.toEpochMilli(),
                bucketEnd.toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "DistanceBucketRecorded.v1",
                bucketEnd,
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createCaloriesEnvelope(GoogleFitBucketData bucket) {
        Instant bucketStart = bucket.bucketStart();
        Instant bucketEnd = bucket.bucketEnd();

        ActiveCaloriesPayload payload = new ActiveCaloriesPayload(
                bucketStart,
                bucketEnd,
                (double) bucket.calories().intValue(),
                GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|calories|%d|%d",
                bucketStart.toEpochMilli(),
                bucketEnd.toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "ActiveCaloriesBurnedRecorded.v1",
                bucketEnd,
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createHeartRateEnvelope(GoogleFitBucketData bucket) {
        List<Integer> heartRates = bucket.heartRates();
        double avg = heartRates.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int min = heartRates.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = heartRates.stream().mapToInt(Integer::intValue).max().orElse(0);

        Instant bucketStart = bucket.bucketStart();
        Instant bucketEnd = bucket.bucketEnd();

        HeartRatePayload payload = new HeartRatePayload(
                bucketStart,
                bucketEnd,
                avg,
                min,
                max,
                heartRates.size(),
                GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|heart-rate|%d|%d",
                bucketStart.toEpochMilli(),
                bucketEnd.toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "HeartRateSummaryRecorded.v1",
                bucketEnd,
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createActiveMinutesEnvelope(GoogleFitBucketData bucket) {
        Instant bucketStart = bucket.bucketStart();
        Instant bucketEnd = bucket.bucketEnd();

        ActiveMinutesPayload payload = new ActiveMinutesPayload(
                bucketStart,
                bucketEnd,
                1,
                GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|active-minutes|%d|%d",
                bucketStart.toEpochMilli(),
                bucketEnd.toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "ActiveMinutesRecorded.v1",
                bucketEnd,
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createSleepSessionEnvelope(GoogleFitSession session) {
        Instant start = session.getStartTime();
        Instant end = session.getEndTime();

        if (start == null || end == null) {
            throw new IllegalArgumentException("Sleep session must have start and end times");
        }

        long totalMinutes = java.time.Duration.between(start, end).toMinutes();

        String sleepId = session.id() != null ? session.id() : String.format("%d-%d", start.toEpochMilli(), end.toEpochMilli());

        SleepSessionPayload payload = new SleepSessionPayload(
                sleepId,
                start,
                end,
                (int) totalMinutes,
                session.getPackageName() != null ? session.getPackageName() : GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|sleep|%s|%s", DEFAULT_USER_ID, sleepId);

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "SleepSessionRecorded.v1",
                end,
                payload
        );
    }

    List<StoreHealthEventsCommand.EventEnvelope> mapWalkingSessionsToEnvelopes(
            List<GoogleFitSession> sessions,
            List<GoogleFitBucketData> availableBuckets
    ) {
        return sessions.stream()
                .map(session -> createWalkingSessionEnvelope(session, availableBuckets))
                .filter(Objects::nonNull)
                .toList();
    }

    private StoreHealthEventsCommand.EventEnvelope createWalkingSessionEnvelope(
            GoogleFitSession session,
            List<GoogleFitBucketData> availableBuckets
    ) {
        Instant start = session.getStartTime();
        Instant end = session.getEndTime();

        if (start == null || end == null) {
            log.warn("Walking session {} has missing start or end time", session.id());
            return null;
        }

        List<GoogleFitBucketData> sessionBuckets = availableBuckets.stream()
                .filter(bucket -> {
                    Instant bucketStart = bucket.bucketStart();
                    Instant bucketEnd = bucket.bucketEnd();
                    return !bucketEnd.isBefore(start) && !bucketStart.isAfter(end);
                })
                .toList();

        int totalSteps = (int) sessionBuckets.stream()
                .filter(b -> b.steps() != null && b.steps() > 0)
                .mapToLong(GoogleFitBucketData::steps)
                .sum();

        double totalDistanceSum = sessionBuckets.stream()
                .filter(b -> b.distance() != null && b.distance() > 0)
                .mapToDouble(GoogleFitBucketData::distance)
                .sum();
        Long totalDistance = totalDistanceSum > 0 ? Math.round(totalDistanceSum) : null;

        double totalCaloriesSum = sessionBuckets.stream()
                .filter(b -> b.calories() != null && b.calories() > 0)
                .mapToDouble(GoogleFitBucketData::calories)
                .sum();
        Integer totalCalories = totalCaloriesSum > 0 ? (int) Math.round(totalCaloriesSum) : null;

        List<Integer> allHeartRates = sessionBuckets.stream()
                .filter(b -> b.heartRates() != null && !b.heartRates().isEmpty())
                .flatMap(b -> b.heartRates().stream())
                .collect(Collectors.toList());

        Integer avgHeartRate = allHeartRates.isEmpty() ? null :
                (int) Math.round(allHeartRates.stream().mapToInt(Integer::intValue).average().orElse(0.0));
        Integer maxHeartRate = allHeartRates.isEmpty() ? null :
                allHeartRates.stream().mapToInt(Integer::intValue).max().orElse(0);

        long durationMinutes = java.time.Duration.between(start, end).toMinutes();

        String sessionId = session.id() != null ? session.id() : String.format("%d-%d", start.toEpochMilli(), end.toEpochMilli());

        WalkingSessionPayload payload = new WalkingSessionPayload(
                sessionId,
                session.name(),
                start,
                end,
                (int) durationMinutes,
                totalSteps,
                totalDistance,
                totalCalories,
                avgHeartRate != null && avgHeartRate > 0 ? avgHeartRate : null,
                maxHeartRate != null && maxHeartRate > 0 ? maxHeartRate : null,
                !allHeartRates.isEmpty() ? allHeartRates : null,
                session.getPackageName() != null ? session.getPackageName() : GOOGLE_FIT_ORIGIN
        );

        String idempotencyKey = String.format("google-fit|walking|%s|%s", DEFAULT_USER_ID, sessionId);

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "WalkingSessionRecorded.v1",
                end,
                payload
        );
    }

    private boolean isActiveMinute(Integer steps, Double distanceMeters) {
        int s = steps != null ? steps : 0;
        double d = distanceMeters != null ? distanceMeters : 0.0;

        if (s >= 100) {
            return true;
        }

        if (s >= 30) {
            return true;
        }

        return d >= 20.0;
    }
}
