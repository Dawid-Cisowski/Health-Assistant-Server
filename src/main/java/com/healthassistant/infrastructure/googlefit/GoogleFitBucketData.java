package com.healthassistant.infrastructure.googlefit;

import java.time.Instant;
import java.util.List;

public record GoogleFitBucketData(
        Instant bucketStart,
        Instant bucketEnd,
        Long steps,
        Double distance,
        Double calories,
        List<Integer> heartRates,
        List<GoogleFitSleepSegment> sleepSegments
) {
}

