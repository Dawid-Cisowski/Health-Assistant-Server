package com.healthassistant.googlefit;

import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.googlefit.api.HistoricalSyncResult;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class GoogleFitSyncService implements GoogleFitFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DeviceId GOOGLE_FIT_DEVICE_ID = DeviceId.of("google-fit");
    private static final DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final GoogleFitClient googleFitClient;
    private final GoogleFitBucketMapper bucketMapper;
    private final GoogleFitEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final HistoricalSyncTaskRepository historicalSyncTaskRepository;
    private final HistoricalSyncTaskProcessor historicalSyncTaskProcessor;

    @Override
    public HistoricalSyncResult syncHistory(int days) {
        log.info("Scheduling historical Google Fit synchronization for {} days", days);

        LocalDate endDate = LocalDate.now(POLAND_ZONE);
        LocalDate startDate = endDate.minusDays(days - 1);

        log.info("Historical sync range: {} to {} ({} days)", startDate, endDate, days);

        int scheduledCount = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            boolean alreadyExists = historicalSyncTaskRepository.existsBySyncDateAndStatusIn(
                    currentDate,
                    List.of(HistoricalSyncTask.SyncTaskStatus.PENDING, HistoricalSyncTask.SyncTaskStatus.IN_PROGRESS)
            );

            if (!alreadyExists) {
                HistoricalSyncTask task = new HistoricalSyncTask(currentDate);
                historicalSyncTaskRepository.save(task);
                scheduledCount++;
                log.debug("Scheduled sync task for date: {}", currentDate);
            } else {
                log.debug("Skipping date {} - task already exists", currentDate);
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Scheduled {} historical sync tasks", scheduledCount);

        // Trigger immediate processing
        historicalSyncTaskProcessor.processNextBatch();

        return new HistoricalSyncResult(scheduledCount, 0, 0);
    }

    int syncTimeWindow(Instant from, Instant to) {
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
