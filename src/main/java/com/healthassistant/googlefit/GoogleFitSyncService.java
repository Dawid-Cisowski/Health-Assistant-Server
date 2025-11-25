package com.healthassistant.googlefit;

import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.googlefit.api.HistoricalSyncResult;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.model.DeviceId;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
class GoogleFitSyncService implements GoogleFitFacade {

    private static final String DEFAULT_USER_ID = "default";
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long BUCKET_DURATION_MINUTES = 1;
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

    @Override
    public void syncAll() {
        log.info("Starting Google Fit synchronization");
        
        try {
            Instant now = Instant.now();
            Instant from = getSyncFromTime();
            Instant to = roundToNextBucket(now);

            log.info("Syncing Google Fit data from {} to {}", from, to);

            var aggregateRequest = getAggregateRequest(from, to);
            var aggregateResponse = googleFitClient.fetchAggregated(aggregateRequest);
            List<GoogleFitBucketData> buckets = bucketMapper.mapBuckets(aggregateResponse);

            String startTimeRfc3339 = RFC3339_FORMATTER.format(from);
            String endTimeRfc3339 = RFC3339_FORMATTER.format(to);
            var sessionsResponse = googleFitClient.fetchSessions(startTimeRfc3339, endTimeRfc3339, false);
            List<GoogleFitSession> allSessions = sessionsResponse.sessions() != null
                    ? sessionsResponse.sessions()
                    : List.of();

            List<GoogleFitSession> sleepSessions = allSessions.stream()
                    .filter(GoogleFitSession::isSleepSession)
                    .toList();

            List<GoogleFitSession> walkingSessions = allSessions.stream()
                    .filter(GoogleFitSession::isWalkingSession)
                    .toList();

            log.info("Fetched {} buckets, {} sleep sessions, and {} walking sessions", 
                    buckets.size(), sleepSessions.size(), walkingSessions.size());

            List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();
            
            eventEnvelopes.addAll(eventMapper.mapToEventEnvelopes(buckets));
            eventEnvelopes.addAll(eventMapper.mapSleepSessionsToEnvelopes(sleepSessions));
            eventEnvelopes.addAll(eventMapper.mapWalkingSessionsToEnvelopes(walkingSessions, buckets));

            if (eventEnvelopes.isEmpty()) {
                log.info("No events to process");
                updateLastSyncedAt(to);
                return;
            }

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, GOOGLE_FIT_DEVICE_ID);
            healthEventsFacade.storeHealthEvents(command);

            updateLastSyncedAt(to);

            log.info("Successfully synchronized {} events from Google Fit ({} from aggregate, {} sleep sessions, {} walking sessions)",
                    eventEnvelopes.size(),
                    buckets.stream().mapToInt(b -> {
                        int count = 0;
                        if (b.steps() != null && b.steps() > 0) count++;
                        if (b.distance() != null && b.distance() > 0) count++;
                        if (b.calories() != null && b.calories() > 0) count++;
                        if (b.heartRates() != null && !b.heartRates().isEmpty()) count++;
                        return count;
                    }).sum(),
                    sleepSessions.size(),
                    walkingSessions.size());

        } catch (Exception e) {
            log.error("Failed to synchronize Google Fit data", e);
            throw e;
        }
    }

    private static GoogleFitClient.AggregateRequest getAggregateRequest(Instant from, Instant to) {
        return new GoogleFitClient.AggregateRequest(
                List.of(
                        new GoogleFitClient.DataTypeAggregate("com.google.step_count.delta"),
                        new GoogleFitClient.DataTypeAggregate("com.google.distance.delta"),
                        new GoogleFitClient.DataTypeAggregate("com.google.calories.expended"),
                        new GoogleFitClient.DataTypeAggregate("com.google.heart_rate.bpm")
                ),
                new GoogleFitClient.BucketByTime(60000L),
                from.toEpochMilli(),
                to.toEpochMilli()
        );
    }

    private Instant getSyncFromTime() {
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

    @Override
    public HistoricalSyncResult syncHistory(int days) {
        log.info("Starting historical Google Fit synchronization for {} days", days);
        
        LocalDate endDate = LocalDate.now(POLAND_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1);
        
        log.info("Historical sync range: {} to {} ({} days)", startDate, endDate, days);
        
        List<LocalDate> datesToProcess = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            datesToProcess.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }
        
        AtomicInteger totalEvents = new AtomicInteger(0);
        AtomicInteger processedDays = new AtomicInteger(0);
        AtomicInteger failedDays = new AtomicInteger(0);
        
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> allFutures = new ArrayList<>();
            
            for (LocalDate date : datesToProcess) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        ZonedDateTime dayStart = date.atStartOfDay(POLAND_ZONE);
                        ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE);
                        
                        Instant from = dayStart.toInstant();
                        Instant to = dayEnd.toInstant();
                        
                        log.info("Processing day {}: {} to {}", date, from, to);
                        
                        int dayEvents = syncTimeWindow(from, to);
                        totalEvents.addAndGet(dayEvents);
                        processedDays.incrementAndGet();
                        
                        log.info("Day {} processed: {} events", date, dayEvents);
                        
                    } catch (Exception e) {
                        log.error("Failed to process day {}", date, e);
                        failedDays.incrementAndGet();
                    }
                }, executor);
                
                allFutures.add(future);
            }
            
            CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();
        }
        
        log.info("Historical sync completed: {} days processed, {} failed, {} total events", 
                processedDays.get(), failedDays.get(), totalEvents.get());
        
        return new HistoricalSyncResult(processedDays.get(), failedDays.get(), totalEvents.get());
    }
    
    private int syncTimeWindow(Instant from, Instant to) {
        var aggregateRequest = getAggregateRequest(from, to);
        var aggregateResponse = googleFitClient.fetchAggregated(aggregateRequest);
        List<GoogleFitBucketData> buckets = bucketMapper.mapBuckets(aggregateResponse);
        
        String startTimeRfc3339 = RFC3339_FORMATTER.format(from);
        String endTimeRfc3339 = RFC3339_FORMATTER.format(to);
        var sessionsResponse = googleFitClient.fetchSessions(startTimeRfc3339, endTimeRfc3339, false);
        List<GoogleFitSession> allSessions = sessionsResponse.sessions() != null
                ? sessionsResponse.sessions()
                : List.of();

        List<GoogleFitSession> sleepSessions = allSessions.stream()
                .filter(GoogleFitSession::isSleepSession)
                .toList();

        List<GoogleFitSession> walkingSessions = allSessions.stream()
                .filter(GoogleFitSession::isWalkingSession)
                .toList();
        
        List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();
        
        eventEnvelopes.addAll(eventMapper.mapToEventEnvelopes(buckets));
        eventEnvelopes.addAll(eventMapper.mapSleepSessionsToEnvelopes(sleepSessions));
        eventEnvelopes.addAll(eventMapper.mapWalkingSessionsToEnvelopes(walkingSessions, buckets));
        
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
}

