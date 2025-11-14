package com.healthassistant.infrastructure.googlefit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleFitBucketMapper {

    public List<GoogleFitBucketData> mapBuckets(GoogleFitAggregateResponse response) {
        if (response == null || response.getBuckets() == null) {
            return List.of();
        }

        return response.getBuckets().stream()
                .map(this::mapBucket)
                .toList();
    }

    private GoogleFitBucketData mapBucket(GoogleFitAggregateResponse.GoogleFitBucket bucket) {
        Instant bucketStart = Instant.ofEpochMilli(bucket.getStartTimeMillis());
        Instant bucketEnd = Instant.ofEpochMilli(bucket.getEndTimeMillis());

        BucketDataExtractor extractor = new BucketDataExtractor();
        
        if (bucket.getDatasets() != null) {
            bucket.getDatasets().forEach(dataset -> extractor.extractFromDataset(dataset));
        }

        return new GoogleFitBucketData(
                bucketStart,
                bucketEnd,
                extractor.getSteps(),
                extractor.getDistance(),
                extractor.getCalories(),
                extractor.getHeartRates().isEmpty() ? null : extractor.getHeartRates(),
                extractor.getSleepSegments().isEmpty() ? null : extractor.getSleepSegments()
        );
    }

    private static class BucketDataExtractor {
        private Long steps;
        private Double distance;
        private Double calories;
        private final List<Integer> heartRates = new ArrayList<>();
        private final List<GoogleFitSleepSegment> sleepSegments = new ArrayList<>();

        void extractFromDataset(GoogleFitAggregateResponse.GoogleFitBucket.Dataset dataset) {
            String dataSourceId = dataset.getDataSourceId();
            if (dataSourceId == null || dataset.getPoints() == null) {
                return;
            }

            dataset.getPoints().stream()
                    .filter(point -> point.getValues() != null && !point.getValues().isEmpty())
                    .forEach(point -> extractFromDataPoint(dataSourceId, point));
        }

        private void extractFromDataPoint(String dataSourceId, GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint point) {
            point.getValues().forEach(value -> {
                if (dataSourceId.contains("step_count")) {
                    steps = extractLongValue(value);
                } else if (dataSourceId.contains("distance")) {
                    distance = extractDoubleValue(value);
                } else if (dataSourceId.contains("calories")) {
                    calories = extractDoubleValue(value);
                } else if (dataSourceId.contains("heart_rate")) {
                    Long hr = extractLongValue(value);
                    if (hr != null) {
                        heartRates.add(hr.intValue());
                    }
                } else if (dataSourceId.contains("sleep")) {
                    GoogleFitSleepSegment segment = extractSleepSegment(point, value);
                    if (segment != null) {
                        sleepSegments.add(segment);
                    }
                }
            });
        }

        Long getSteps() { return steps; }
        Double getDistance() { return distance; }
        Double getCalories() { return calories; }
        List<Integer> getHeartRates() { return heartRates; }
        List<GoogleFitSleepSegment> getSleepSegments() { return sleepSegments; }
    }

    private static Long extractLongValue(GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint.Value value) {
        if (value.getIntVal() != null) {
            return value.getIntVal();
        }
        if (value.getFpVal() != null) {
            return value.getFpVal().longValue();
        }
        return null;
    }

    private static Double extractDoubleValue(GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint.Value value) {
        if (value.getFpVal() != null) {
            return value.getFpVal();
        }
        if (value.getIntVal() != null) {
            return value.getIntVal().doubleValue();
        }
        return null;
    }

    private static GoogleFitSleepSegment extractSleepSegment(
            GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint point,
            GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint.Value value
    ) {
        if (point.getStartTimeNanos() == null || point.getEndTimeNanos() == null) {
            return null;
        }

        long startNanos = Long.parseLong(point.getStartTimeNanos());
        long endNanos = Long.parseLong(point.getEndTimeNanos());
        Instant start = Instant.ofEpochSecond(startNanos / 1_000_000_000L, startNanos % 1_000_000_000L);
        Instant end = Instant.ofEpochSecond(endNanos / 1_000_000_000L, endNanos % 1_000_000_000L);

        Long sleepType = extractSleepType(value);
        return new GoogleFitSleepSegment(start, end, sleepType);
    }

    private static Long extractSleepType(GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint.Value value) {
        if (value.getMapVal() == null || value.getMapVal().isEmpty()) {
            return null;
        }

        return value.getMapVal().stream()
                .filter(entry -> "sleep_type".equals(entry.getKey()) && entry.getValue() != null)
                .findFirst()
                .map(entry -> entry.getValue().getIntVal())
                .orElse(null);
    }
}

