package com.healthassistant.googlefit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class GoogleFitBucketMapper {

    public List<GoogleFitBucketData> mapBuckets(GoogleFitAggregateResponse response) {
        if (response == null || response.buckets() == null) {
            return List.of();
        }

        return response.buckets().stream()
                .map(this::mapBucket)
                .toList();
    }

    private GoogleFitBucketData mapBucket(GoogleFitAggregateResponse.GoogleFitBucket bucket) {
        Instant bucketStart = Instant.ofEpochMilli(bucket.startTimeMillis());
        Instant bucketEnd = Instant.ofEpochMilli(bucket.endTimeMillis());

        BucketDataExtractor extractor = new BucketDataExtractor();

        if (bucket.datasets() != null) {
            bucket.datasets().forEach(dataset -> extractor.extractFromDataset(dataset));
        }

        return new GoogleFitBucketData(
                bucketStart,
                bucketEnd,
                extractor.getSteps(),
                extractor.getDistance(),
                extractor.getCalories(),
                extractor.getHeartRates().isEmpty() ? null : extractor.getHeartRates()
        );
    }

    private static class BucketDataExtractor {
        private Long steps;
        private Double distance;
        private Double calories;
        private final List<Integer> heartRates = new ArrayList<>();

        void extractFromDataset(GoogleFitAggregateResponse.GoogleFitBucket.Dataset dataset) {
            String dataSourceId = dataset.dataSourceId();
            if (dataSourceId == null || dataset.points() == null) {
                return;
            }

            dataset.points().stream()
                    .filter(point -> point.values() != null && !point.values().isEmpty())
                    .forEach(point -> extractFromDataPoint(dataSourceId, point));
        }

        private void extractFromDataPoint(String dataSourceId, GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint point) {
            point.values().forEach(value -> {
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
                }
            });
        }

        Long getSteps() { return steps; }
        Double getDistance() { return distance; }
        Double getCalories() { return calories; }
        List<Integer> getHeartRates() { return heartRates; }
    }

    private static Long extractLongValue(GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint.Value value) {
        if (value.intVal() != null) {
            return value.intVal();
        }
        if (value.fpVal() != null) {
            return value.fpVal().longValue();
        }
        return null;
    }

    private static Double extractDoubleValue(GoogleFitAggregateResponse.GoogleFitBucket.Dataset.DataPoint.Value value) {
        if (value.fpVal() != null) {
            return value.fpVal();
        }
        if (value.intVal() != null) {
            return value.intVal().doubleValue();
        }
        return null;
    }
}
