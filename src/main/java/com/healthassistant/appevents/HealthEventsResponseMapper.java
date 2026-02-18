package com.healthassistant.appevents;

import com.healthassistant.appevents.api.dto.SubmitHealthEventsResponse;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsResponse.ErrorDetail;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsResponse.EventResult;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsResponse.Summary;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
class HealthEventsResponseMapper {

    SubmitHealthEventsResponse toResponse(StoreHealthEventsResult result, int totalEvents) {
        List<EventResult> eventResults = mapEventResults(result.results());
        Summary summary = calculateSummary(result.results());
        String status = determineOverallStatus(summary, totalEvents);

        logProcessingResult(status, totalEvents, summary);

        return new SubmitHealthEventsResponse(status, totalEvents, summary, eventResults);
    }

    private List<EventResult> mapEventResults(List<StoreHealthEventsResult.EventResult> results) {
        return results.stream()
                .map(this::mapSingleEventResult)
                .toList();
    }

    private EventResult mapSingleEventResult(StoreHealthEventsResult.EventResult eventResult) {
        ErrorDetail errorDetail = Optional.ofNullable(eventResult.error())
                .map(error -> new ErrorDetail(error.field(), error.message()))
                .orElse(null);

        String eventId = Optional.ofNullable(eventResult.eventId())
                .map(id -> id.value())
                .orElse(null);

        return new EventResult(
                eventResult.index(),
                eventResult.status().toString(),
                eventId,
                errorDetail
        );
    }

    private Summary calculateSummary(List<StoreHealthEventsResult.EventResult> results) {
        Map<EventStatus, Long> statusCounts = results.stream()
                .map(StoreHealthEventsResult.EventResult::status)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return new Summary(
                statusCounts.getOrDefault(EventStatus.STORED, 0L),
                statusCounts.getOrDefault(EventStatus.DUPLICATE, 0L),
                statusCounts.getOrDefault(EventStatus.INVALID, 0L)
        );
    }

    private String determineOverallStatus(Summary summary, int totalEvents) {
        if (summary.invalid() == totalEvents) {
            return "all_invalid";
        }
        if (summary.invalid() > 0) {
            return "partial_success";
        }
        return "success";
    }

    private void logProcessingResult(String status, int totalEvents, Summary summary) {
        record LogEntry(String message, boolean isWarning) {}

        LogEntry entry = switch (status) {
            case "all_invalid" -> new LogEntry(
                    "All %d events were invalid".formatted(totalEvents),
                    true
            );
            case "partial_success" -> new LogEntry(
                    "Processed %d events: %d stored, %d duplicate, %d invalid".formatted(
                            totalEvents, summary.stored(), summary.duplicate(), summary.invalid()
                    ),
                    false
            );
            default -> new LogEntry(
                    "Successfully processed %d events: %d stored, %d duplicate".formatted(
                            totalEvents, summary.stored(), summary.duplicate()
                    ),
                    false
            );
        };

        if (entry.isWarning()) {
            log.warn(entry.message());
        } else {
            log.info(entry.message());
        }
    }
}
