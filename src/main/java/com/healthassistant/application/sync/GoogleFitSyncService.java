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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleFitSyncService {

    private static final String DEFAULT_USER_ID = "default";
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long BUCKET_DURATION_MINUTES = 5;
    private static final long BUFFER_HOURS = 1;
    private static final DeviceId GOOGLE_FIT_DEVICE_ID = DeviceId.of("google-fit");
    private static final DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final GoogleFitClient googleFitClient;
    private final GoogleFitBucketMapper bucketMapper;
    private final GoogleFitEventMapper eventMapper;
    private final GoogleFitSyncStateRepository syncStateRepository;
    private final HealthEventsFacade healthEventsFacade;

    @Scheduled(cron = "0 */15 * * * *")
    public void syncGoogleFitData() {
        syncAll();
    }

    public void syncAll() {
        log.info("Starting Google Fit synchronization");
        
        try {
            Instant now = Instant.now();
            Instant from = getSyncFromTime(now);
            Instant to = roundToNextBucket(now);

            log.info("Syncing Google Fit data from {} to {}", from, to);

            var aggregateRequest = getAggregateRequest(from, to);
            var aggregateResponse = googleFitClient.fetchAggregated(aggregateRequest);
            List<com.healthassistant.infrastructure.googlefit.GoogleFitBucketData> buckets = bucketMapper.mapBuckets(aggregateResponse);

            String startTimeRfc3339 = RFC3339_FORMATTER.format(from);
            String endTimeRfc3339 = RFC3339_FORMATTER.format(to);
            var sessionsResponse = googleFitClient.fetchSessions(startTimeRfc3339, endTimeRfc3339, false);
            List<com.healthassistant.infrastructure.googlefit.GoogleFitSession> sleepSessions = sessionsResponse.getSessions() != null
                    ? sessionsResponse.getSessions().stream()
                            .filter(com.healthassistant.infrastructure.googlefit.GoogleFitSession::isSleepSession)
                            .toList()
                    : List.of();

            log.info("Fetched {} buckets and {} sleep sessions", buckets.size(), sleepSessions.size());

            List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();
            
            eventEnvelopes.addAll(eventMapper.mapToEventEnvelopes(buckets));
            eventEnvelopes.addAll(eventMapper.mapSleepSessionsToEnvelopes(sleepSessions));

            if (eventEnvelopes.isEmpty()) {
                log.info("No events to process");
                updateLastSyncedAt(to);
                return;
            }

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, GOOGLE_FIT_DEVICE_ID);
            healthEventsFacade.storeHealthEvents(command);

            updateLastSyncedAt(to);

            log.info("Successfully synchronized {} events from Google Fit ({} from aggregate, {} from sessions)",
                    eventEnvelopes.size(),
                    buckets.stream().mapToInt(b -> {
                        int count = 0;
                        if (b.steps() != null && b.steps() > 0) count++;
                        if (b.distance() != null && b.distance() > 0) count++;
                        if (b.calories() != null && b.calories() > 0) count++;
                        if (b.heartRates() != null && !b.heartRates().isEmpty()) count++;
                        return count;
                    }).sum(),
                    sleepSessions.size());

        } catch (Exception e) {
            log.error("Failed to synchronize Google Fit data", e);
            throw e;
        }
    }

    private static GoogleFitClient.AggregateRequest getAggregateRequest(Instant from, Instant to) {
        var request = new GoogleFitClient.AggregateRequest();
        request.setAggregateBy(List.of(
                new GoogleFitClient.DataTypeAggregate("com.google.step_count.delta"),
                new GoogleFitClient.DataTypeAggregate("com.google.distance.delta"),
                new GoogleFitClient.DataTypeAggregate("com.google.calories.expended"),
                new GoogleFitClient.DataTypeAggregate("com.google.heart_rate.bpm")
        ));
        request.setBucketByTime(new GoogleFitClient.BucketByTime(300000L));
        request.setStartTimeMillis(from.toEpochMilli());
        request.setEndTimeMillis(to.toEpochMilli());
        return request;
    }

    private Instant getSyncFromTime(Instant now) {
        GoogleFitSyncState state = syncStateRepository.findByUserId(DEFAULT_USER_ID)
                .orElse(null);

        if (state == null) {
            ZonedDateTime yesterday = LocalDate.now(POLAND_ZONE)
                    .minusDays(1)
                    .atStartOfDay(POLAND_ZONE);
            Instant firstSync = yesterday.toInstant();
            log.info("First sync detected, starting from yesterday: {}", firstSync);
            return firstSync;
        }

        Instant lastSyncedMinusBuffer = state.getLastSyncedAt().minus(BUFFER_HOURS, ChronoUnit.HOURS);
        ZonedDateTime yesterdayStart = LocalDate.now(POLAND_ZONE)
                .minusDays(1)
                .atStartOfDay(POLAND_ZONE);
        Instant yesterday = yesterdayStart.toInstant();
        
        Instant syncFrom = lastSyncedMinusBuffer.isBefore(yesterday) ? lastSyncedMinusBuffer : yesterday;
        log.info("Sync from time: {} (lastSynced: {}, yesterday: {})", syncFrom, state.getLastSyncedAt(), yesterday);
        return syncFrom;
    }

    private Instant roundToNextBucket(Instant instant) {
        long epochSeconds = instant.getEpochSecond();
        long bucketDurationSeconds = BUCKET_DURATION_MINUTES * 60;
        long nextBucketStart = ((epochSeconds / bucketDurationSeconds) + 1) * bucketDurationSeconds;
        return Instant.ofEpochSecond(nextBucketStart);
    }

    public HistoricalSyncResult syncHistory(int days) {
        log.info("Starting historical Google Fit synchronization for {} days", days);
        
        LocalDate endDate = LocalDate.now(POLAND_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1);
        
        log.info("Historical sync range: {} to {} ({} days)", startDate, endDate, days);
        
        int totalEvents = 0;
        int processedDays = 0;
        int failedDays = 0;
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            try {
                ZonedDateTime dayStart = currentDate.atStartOfDay(POLAND_ZONE);
                ZonedDateTime dayEnd = currentDate.plusDays(1).atStartOfDay(POLAND_ZONE);
                
                Instant from = dayStart.toInstant();
                Instant to = dayEnd.toInstant();
                
                log.info("Processing day {}: {} to {}", currentDate, from, to);
                
                int dayEvents = syncTimeWindow(from, to);
                totalEvents += dayEvents;
                processedDays++;
                
                log.info("Day {} processed: {} events", currentDate, dayEvents);
                
            } catch (Exception e) {
                log.error("Failed to process day {}", currentDate, e);
                failedDays++;
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        log.info("Historical sync completed: {} days processed, {} failed, {} total events", 
                processedDays, failedDays, totalEvents);
        
        return new HistoricalSyncResult(processedDays, failedDays, totalEvents);
    }
    
    private int syncTimeWindow(Instant from, Instant to) {
        var aggregateRequest = getAggregateRequest(from, to);
        var aggregateResponse = googleFitClient.fetchAggregated(aggregateRequest);
        List<com.healthassistant.infrastructure.googlefit.GoogleFitBucketData> buckets = bucketMapper.mapBuckets(aggregateResponse);
        
        String startTimeRfc3339 = RFC3339_FORMATTER.format(from);
        String endTimeRfc3339 = RFC3339_FORMATTER.format(to);
        var sessionsResponse = googleFitClient.fetchSessions(startTimeRfc3339, endTimeRfc3339, false);
        List<com.healthassistant.infrastructure.googlefit.GoogleFitSession> sleepSessions = sessionsResponse.getSessions() != null
                ? sessionsResponse.getSessions().stream()
                        .filter(com.healthassistant.infrastructure.googlefit.GoogleFitSession::isSleepSession)
                        .toList()
                : List.of();
        
        List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();
        
        eventEnvelopes.addAll(eventMapper.mapToEventEnvelopes(buckets));
        eventEnvelopes.addAll(eventMapper.mapSleepSessionsToEnvelopes(sleepSessions));
        
        if (eventEnvelopes.isEmpty()) {
            return 0;
        }
        
        StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, GOOGLE_FIT_DEVICE_ID);
        healthEventsFacade.storeHealthEvents(command);
        
        return eventEnvelopes.size();
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
    
    public record HistoricalSyncResult(int processedDays, int failedDays, int totalEvents) {
    }
}

