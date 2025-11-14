package com.healthassistant.application.sync;

import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.domain.event.IdempotencyKey;
import com.healthassistant.infrastructure.googlefit.GoogleFitBucketData;
import com.healthassistant.infrastructure.googlefit.GoogleFitSleepSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class GoogleFitEventMapper {

    private static final String GOOGLE_FIT_ORIGIN = "google-fit";
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

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

        if (bucket.sleepSegments() != null && !bucket.sleepSegments().isEmpty()) {
            bucket.sleepSegments().stream()
                    .map(this::createSleepEnvelope)
                    .forEach(envelopes::add);
        }

        return envelopes.stream();
    }

    private StoreHealthEventsCommand.EventEnvelope createStepsEnvelope(GoogleFitBucketData bucket) {
        ZonedDateTime bucketStartPoland = bucket.bucketStart().atZone(POLAND_ZONE);
        ZonedDateTime bucketEndPoland = bucket.bucketEnd().atZone(POLAND_ZONE);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("bucketStart", bucketStartPoland.format(ISO_FORMATTER));
        payload.put("bucketEnd", bucketEndPoland.format(ISO_FORMATTER));
        payload.put("count", bucket.steps());
        payload.put("originPackage", GOOGLE_FIT_ORIGIN);

        String idempotencyKey = String.format("google-fit|steps|%d|%d",
                bucket.bucketStart().toEpochMilli(),
                bucket.bucketEnd().toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "StepsBucketedRecorded.v1",
                bucket.bucketStart(),
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createDistanceEnvelope(GoogleFitBucketData bucket) {
        ZonedDateTime bucketStartPoland = bucket.bucketStart().atZone(POLAND_ZONE);
        ZonedDateTime bucketEndPoland = bucket.bucketEnd().atZone(POLAND_ZONE);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("bucketStart", bucketStartPoland.format(ISO_FORMATTER));
        payload.put("bucketEnd", bucketEndPoland.format(ISO_FORMATTER));
        payload.put("distanceMeters", bucket.distance());
        payload.put("originPackage", GOOGLE_FIT_ORIGIN);

        String idempotencyKey = String.format("google-fit|distance|%d|%d",
                bucket.bucketStart().toEpochMilli(),
                bucket.bucketEnd().toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "DistanceBucketRecorded.v1",
                bucket.bucketStart(),
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createCaloriesEnvelope(GoogleFitBucketData bucket) {
        ZonedDateTime bucketStartPoland = bucket.bucketStart().atZone(POLAND_ZONE);
        ZonedDateTime bucketEndPoland = bucket.bucketEnd().atZone(POLAND_ZONE);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("bucketStart", bucketStartPoland.format(ISO_FORMATTER));
        payload.put("bucketEnd", bucketEndPoland.format(ISO_FORMATTER));
        payload.put("energyKcal", bucket.calories().intValue());
        payload.put("originPackage", GOOGLE_FIT_ORIGIN);

        String idempotencyKey = String.format("google-fit|calories|%d|%d",
                bucket.bucketStart().toEpochMilli(),
                bucket.bucketEnd().toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "ActiveCaloriesBurnedRecorded.v1",
                bucket.bucketStart(),
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createHeartRateEnvelope(GoogleFitBucketData bucket) {
        List<Integer> heartRates = bucket.heartRates();
        int avg = (int) heartRates.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int min = heartRates.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = heartRates.stream().mapToInt(Integer::intValue).max().orElse(0);

        ZonedDateTime bucketStartPoland = bucket.bucketStart().atZone(POLAND_ZONE);
        ZonedDateTime bucketEndPoland = bucket.bucketEnd().atZone(POLAND_ZONE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("bucketStart", bucketStartPoland.format(ISO_FORMATTER));
        payload.put("bucketEnd", bucketEndPoland.format(ISO_FORMATTER));
        payload.put("avg", avg);
        payload.put("min", min);
        payload.put("max", max);
        payload.put("samples", heartRates.size());
        payload.put("originPackage", GOOGLE_FIT_ORIGIN);

        String idempotencyKey = String.format("google-fit|heart-rate|%d|%d",
                bucket.bucketStart().toEpochMilli(),
                bucket.bucketEnd().toEpochMilli());

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "HeartRateSummaryRecorded.v1",
                bucket.bucketStart(),
                payload
        );
    }

    private StoreHealthEventsCommand.EventEnvelope createSleepEnvelope(GoogleFitSleepSegment segment) {
        long totalMinutes = java.time.Duration.between(segment.start(), segment.end()).toMinutes();

        ZonedDateTime sleepStartPoland = segment.start().atZone(POLAND_ZONE);
        ZonedDateTime sleepEndPoland = segment.end().atZone(POLAND_ZONE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sleepStart", sleepStartPoland.format(ISO_FORMATTER));
        payload.put("sleepEnd", sleepEndPoland.format(ISO_FORMATTER));
        payload.put("totalMinutes", (int) totalMinutes);
        if (segment.sleepType() != null) {
            payload.put("sleepType", segment.sleepType());
        }
        payload.put("originPackage", GOOGLE_FIT_ORIGIN);

        String idempotencyKey = String.format("google-fit|sleep|%d|%d|%s",
                segment.start().toEpochMilli(),
                segment.end().toEpochMilli(),
                segment.sleepType() != null ? segment.sleepType() : "unknown");

        return new StoreHealthEventsCommand.EventEnvelope(
                IdempotencyKey.of(idempotencyKey),
                "SleepSessionRecorded.v1",
                segment.start(),
                payload
        );
    }
}

