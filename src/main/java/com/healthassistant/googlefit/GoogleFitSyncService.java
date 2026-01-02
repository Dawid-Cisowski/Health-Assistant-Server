package com.healthassistant.googlefit;

import com.healthassistant.config.AppProperties;
import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.googlefit.api.SyncDayResult;
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
import java.util.List;

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
    private final AppProperties appProperties;

    @Override
    public SyncDayResult syncDay(LocalDate date) {
        log.info("Syncing Google Fit data for date: {}", date);

        validateDate(date);

        Instant from = date.atStartOfDay(POLAND_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        var aggregateResponse = googleFitClient.fetchAggregated(getAggregateRequest(from, to));
        List<GoogleFitBucketData> buckets = bucketMapper.mapBuckets(aggregateResponse);
        log.info("Fetched {} buckets for date {}", buckets.size(), date);

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
        log.info("Fetched {} sleep sessions and {} walking sessions for date {}",
                sleepSessions.size(), walkingSessions.size(), date);

        List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();
        eventEnvelopes.addAll(eventMapper.mapToEventEnvelopes(buckets));
        eventEnvelopes.addAll(eventMapper.mapSleepSessionsToEnvelopes(sleepSessions));
        eventEnvelopes.addAll(eventMapper.mapWalkingSessionsToEnvelopes(walkingSessions, buckets));

        if (eventEnvelopes.isEmpty()) {
            log.info("No events to store for date {}", date);
            return new SyncDayResult(0, 0);
        }

        log.info("Storing {} events for date {}", eventEnvelopes.size(), date);
        DeviceId deviceId = DeviceId.of(appProperties.getGoogleFit().getDeviceId());
        StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, deviceId);
        var result = healthEventsFacade.storeHealthEvents(command);

        int storedCount = (int) result.results().stream()
                .filter(r -> r.status() == StoreHealthEventsResult.EventStatus.stored).count();
        int duplicateCount = (int) result.results().stream()
                .filter(r -> r.status() == StoreHealthEventsResult.EventStatus.duplicate).count();

        log.info("Sync complete for date {}: stored={}, duplicates={}", date, storedCount, duplicateCount);
        return new SyncDayResult(storedCount, duplicateCount);
    }

    private void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Date " + date + " is in the future");
        }
        LocalDate earliestAllowed = today.minusYears(5);
        if (date.isBefore(earliestAllowed)) {
            throw new IllegalArgumentException("Date " + date + " is more than 5 years in the past");
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
                new GoogleFitClient.BucketByTime(900000L),
                from.toEpochMilli(),
                to.toEpochMilli()
        );
    }
}
