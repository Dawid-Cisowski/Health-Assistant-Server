package com.healthassistant.googlefit;

import com.healthassistant.config.AppProperties;
import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.googlefit.api.HistoricalSyncResult;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
class GoogleFitSyncService implements GoogleFitFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final GoogleFitClient googleFitClient;
    private final GoogleFitBucketMapper bucketMapper;
    private final GoogleFitEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final HistoricalSyncTaskRepository historicalSyncTaskRepository;
    private final HistoricalSyncTaskProcessor historicalSyncTaskProcessor;
    private final AppProperties appProperties;

    @Override
    public HistoricalSyncResult syncHistory(int days) {
        log.info("Scheduling historical Google Fit synchronization for {} days", days);

        LocalDate endDate = LocalDate.now(POLAND_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1);

        log.info("Historical sync range: {} to {} ({} days)", startDate, endDate, days);

        List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).toList();
        return scheduleAndProcess(dates);
    }

    @Override
    public HistoricalSyncResult syncDates(List<LocalDate> dates) {
        log.info("Scheduling Google Fit sync for {} specific dates", dates.size());
        validateDates(dates);
        return scheduleAndProcess(dates);
    }

    private HistoricalSyncResult scheduleAndProcess(List<LocalDate> dates) {
        int scheduledCount = 0;

        for (LocalDate date : dates) {
            boolean alreadyExists = historicalSyncTaskRepository.existsBySyncDateAndStatusIn(
                    date,
                    List.of(HistoricalSyncTask.SyncTaskStatus.PENDING, HistoricalSyncTask.SyncTaskStatus.IN_PROGRESS)
            );

            if (!alreadyExists) {
                HistoricalSyncTask task = new HistoricalSyncTask(date);
                historicalSyncTaskRepository.save(task);
                scheduledCount++;
                log.debug("Scheduled sync task for date: {}", date);
            } else {
                log.debug("Skipping date {} - task already exists", date);
            }
        }

        log.info("Scheduled {} historical sync tasks", scheduledCount);

        historicalSyncTaskProcessor.processNextBatch();

        return new HistoricalSyncResult(scheduledCount, 0, 0);
    }

    private void validateDates(List<LocalDate> dates) {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate earliestAllowed = today.minusDays(365);

        Set<LocalDate> seen = new HashSet<>();
        for (LocalDate date : dates) {
            if (date.isAfter(today)) {
                throw new IllegalArgumentException("Date " + date + " is in the future");
            }
            if (date.isBefore(earliestAllowed)) {
                throw new IllegalArgumentException("Date " + date + " is more than 365 days in the past");
            }
            if (!seen.add(date)) {
                throw new IllegalArgumentException("Duplicate date: " + date);
            }
        }
    }

    int syncTimeWindow(Instant from, Instant to) {
        log.info("=== SYNC TIME WINDOW: {} to {} ===", from, to);

        // 1. Fetch aggregated data (steps, distance, calories, heart rate)
        log.info("Fetching aggregated data from Google Fit...");
        var aggregateRequest = getAggregateRequest(from, to);
        var aggregateResponse = googleFitClient.fetchAggregated(aggregateRequest);

        log.info("Aggregate response buckets count: {}",
                aggregateResponse.buckets() != null ? aggregateResponse.buckets().size() : 0);

        List<GoogleFitBucketData> buckets = bucketMapper.mapBuckets(aggregateResponse);
        log.info("Mapped {} buckets from aggregate response", buckets.size());

        // Log bucket statistics
        int bucketsWithSteps = 0;
        int bucketsWithCalories = 0;
        int bucketsWithDistance = 0;
        int bucketsWithHeartRate = 0;
        int totalSteps = 0;
        double totalCalories = 0;

        for (GoogleFitBucketData bucket : buckets) {
            if (bucket.steps() != null && bucket.steps() > 0) {
                bucketsWithSteps++;
                totalSteps += bucket.steps();
            }
            if (bucket.calories() != null && bucket.calories() > 0) {
                bucketsWithCalories++;
                totalCalories += bucket.calories();
            }
            if (bucket.distance() != null && bucket.distance() > 0) {
                bucketsWithDistance++;
            }
            if (bucket.heartRates() != null && !bucket.heartRates().isEmpty()) {
                bucketsWithHeartRate++;
            }
        }
        log.info("Bucket stats: steps={} buckets (total={}), calories={} buckets (total={}), distance={} buckets, heartRate={} buckets",
                bucketsWithSteps, totalSteps, bucketsWithCalories, totalCalories, bucketsWithDistance, bucketsWithHeartRate);

        // 2. Fetch sessions (sleep, walking)
        log.info("Fetching sessions from Google Fit...");
        String startTimeRfc3339 = RFC3339_FORMATTER.format(from);
        String endTimeRfc3339 = RFC3339_FORMATTER.format(to);
        var sessionsResponse = googleFitClient.fetchSessions(startTimeRfc3339, endTimeRfc3339, false);
        List<GoogleFitSession> allSessions = sessionsResponse.sessions() != null
                ? sessionsResponse.sessions()
                : List.of();

        log.info("Sessions response: total={} sessions", allSessions.size());
        allSessions.forEach(s -> log.info("  Session: id={} activityType={} name={} package={} start={} end={}",
                s.id(), s.activityType(), s.name(), s.getPackageName(), s.startTimeMillis(), s.endTimeMillis()));

        List<GoogleFitSession> sleepSessions = allSessions.stream()
                .filter(GoogleFitSession::isSleepSession)
                .toList();
        log.info("Filtered sleep sessions (activityType=72): {}", sleepSessions.size());

        List<GoogleFitSession> walkingSessions = allSessions.stream()
                .filter(GoogleFitSession::isWalkingSession)
                .toList();
        log.info("Filtered walking sessions: {}", walkingSessions.size());

        // 3. Map to event envelopes
        log.info("Mapping to event envelopes...");
        List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();

        var bucketEnvelopes = eventMapper.mapToEventEnvelopes(buckets);
        log.info("Mapped {} envelopes from buckets", bucketEnvelopes.size());
        eventEnvelopes.addAll(bucketEnvelopes);

        var sleepEnvelopes = eventMapper.mapSleepSessionsToEnvelopes(sleepSessions);
        log.info("Mapped {} envelopes from sleep sessions", sleepEnvelopes.size());
        eventEnvelopes.addAll(sleepEnvelopes);

        var walkingEnvelopes = eventMapper.mapWalkingSessionsToEnvelopes(walkingSessions, buckets);
        log.info("Mapped {} envelopes from walking sessions", walkingEnvelopes.size());
        eventEnvelopes.addAll(walkingEnvelopes);

        // Log envelope types breakdown
        var envelopesByType = eventEnvelopes.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        StoreHealthEventsCommand.EventEnvelope::eventType,
                        java.util.stream.Collectors.counting()));
        log.info("Event envelopes by type: {}", envelopesByType);

        if (eventEnvelopes.isEmpty()) {
            log.warn("No events to store for time window {} to {}", from, to);
            return 0;
        }

        // 4. Store events
        log.info("Storing {} events (skipProjections=true)...", eventEnvelopes.size());
        DeviceId deviceId = DeviceId.of(appProperties.getGoogleFit().getDeviceId());
        StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, deviceId, true);
        var result = healthEventsFacade.storeHealthEvents(command);

        long storedCount = result.results().stream()
                .filter(r -> r.status() == StoreHealthEventsResult.EventStatus.stored).count();
        long duplicateCount = result.results().stream()
                .filter(r -> r.status() == StoreHealthEventsResult.EventStatus.duplicate).count();
        long invalidCount = result.results().stream()
                .filter(r -> r.status() == StoreHealthEventsResult.EventStatus.invalid).count();
        log.info("Store result: stored={}, duplicates={}, invalid={}", storedCount, duplicateCount, invalidCount);

        log.info("=== SYNC TIME WINDOW COMPLETE: {} events ===", eventEnvelopes.size());
        return eventEnvelopes.size();
    }

    private static GoogleFitClient.AggregateRequest getAggregateRequest(Instant from, Instant to) {
        return new GoogleFitClient.AggregateRequest(
                List.of(
                        new GoogleFitClient.DataTypeAggregate("com.google.step_count.delta"),
                        new GoogleFitClient.DataTypeAggregate("com.google.distance.delta"),
                        new GoogleFitClient.DataTypeAggregate("com.google.calories.expended"),
                        new GoogleFitClient.DataTypeAggregate("com.google.heart_rate.bpm")
                ),
                new GoogleFitClient.BucketByTime(900000L),
                from.toEpochMilli(),
                to.toEpochMilli()
        );
    }
}
