package com.healthassistant.application.sync;

import com.healthassistant.application.ingestion.HealthEventsFacade;
import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.infrastructure.googlefit.GoogleFitBucketMapper;
import com.healthassistant.infrastructure.googlefit.GoogleFitClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleFitSyncService {

    private static final String DEFAULT_USER_ID = "default";
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long BUCKET_DURATION_MINUTES = 15;
    private static final long BUFFER_HOURS = 1;
    private static final DeviceId GOOGLE_FIT_DEVICE_ID = DeviceId.of("google-fit");

    private final GoogleFitClient googleFitClient;
    private final GoogleFitBucketMapper bucketMapper;
    private final GoogleFitEventMapper eventMapper;
    private final GoogleFitSyncStateRepository syncStateRepository;
    private final HealthEventsFacade healthEventsFacade;

    @Scheduled(cron = "0 */15 * * * *")
    public void syncGoogleFitData() {
        log.info("Starting Google Fit synchronization");
        
        try {
            Instant now = Instant.now();
            Instant from = getSyncFromTime(now);
            Instant to = roundToNextBucket(now);

            log.info("Syncing Google Fit data from {} to {}", from, to);

            var request = getAggregateRequest(from, to);

            var response = googleFitClient.fetchAggregated(request);
            List<com.healthassistant.infrastructure.googlefit.GoogleFitBucketData> buckets = bucketMapper.mapBuckets(response);

            if (buckets.isEmpty()) {
                log.info("No buckets to process");
                updateLastSyncedAt(to);
                return;
            }

            List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = eventMapper.mapToEventEnvelopes(buckets);
            StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, GOOGLE_FIT_DEVICE_ID);
            
            healthEventsFacade.storeHealthEvents(command);

            updateLastSyncedAt(to);

            log.info("Successfully synchronized {} events from Google Fit", eventEnvelopes.size());

        } catch (Exception e) {
            log.error("Failed to synchronize Google Fit data", e);
            throw e; // Re-throw to allow controller to handle error response
        }
    }

    private static GoogleFitClient.AggregateRequest getAggregateRequest(Instant from, Instant to) {
        var request = new GoogleFitClient.AggregateRequest();
        request.setAggregateBy(List.of(
                new GoogleFitClient.DataTypeAggregate("com.google.step_count.delta"),
                new GoogleFitClient.DataTypeAggregate("com.google.distance.delta"),
                new GoogleFitClient.DataTypeAggregate("com.google.calories.expended"),
                new GoogleFitClient.DataTypeAggregate("com.google.heart_rate.bpm"),
                new GoogleFitClient.DataTypeAggregate("com.google.sleep.segment")
        ));
        request.setBucketByTime(new GoogleFitClient.BucketByTime(900000L)); // 15 minutes
        request.setStartTimeMillis(from.toEpochMilli());
        request.setEndTimeMillis(to.toEpochMilli());
        return request;
    }

    private Instant getSyncFromTime(Instant now) {
        GoogleFitSyncState state = syncStateRepository.findByUserId(DEFAULT_USER_ID)
                .orElse(null);

        if (state == null) {
            ZonedDateTime startOfDay = LocalDate.now(POLAND_ZONE)
                    .atStartOfDay(POLAND_ZONE);
            Instant firstSync = startOfDay.toInstant();
            log.info("First sync detected, starting from beginning of day: {}", firstSync);
            return firstSync;
        }

        return state.getLastSyncedAt().minus(BUFFER_HOURS, ChronoUnit.HOURS);
    }

    private Instant roundToNextBucket(Instant instant) {
        long epochSeconds = instant.getEpochSecond();
        long bucketDurationSeconds = BUCKET_DURATION_MINUTES * 60;
        long nextBucketStart = ((epochSeconds / bucketDurationSeconds) + 1) * bucketDurationSeconds;
        return Instant.ofEpochSecond(nextBucketStart);
    }

    private void updateLastSyncedAt(Instant lastSyncedAt) {
        GoogleFitSyncState state = syncStateRepository.findByUserId(DEFAULT_USER_ID)
                .orElse(GoogleFitSyncState.builder()
                        .userId(DEFAULT_USER_ID)
                        .lastSyncedAt(lastSyncedAt)
                        .build());

        state.setLastSyncedAt(lastSyncedAt);
        syncStateRepository.save(state);
        log.info("Updated lastSyncedAt to {}", lastSyncedAt);
    }
}

