package com.healthassistant.googlefit;

import com.healthassistant.config.AppProperties;
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

        DeviceId deviceId = DeviceId.of(appProperties.getGoogleFit().getDeviceId());
        StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, deviceId);
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
