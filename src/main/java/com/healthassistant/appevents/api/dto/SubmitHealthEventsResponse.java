package com.healthassistant.appevents.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response from health events submission")
public record  SubmitHealthEventsResponse(
    @JsonProperty("status")
    @Schema(description = "Overall status", example = "success")
    String status,

    @JsonProperty("totalEvents")
    @Schema(description = "Total number of events submitted", example = "3")
    int totalEvents,

    @JsonProperty("summary")
    @Schema(description = "Summary of results")
    Summary summary,

    @JsonProperty("events")
    @Schema(description = "Detailed results for each event")
    List<EventResult> events
) {

    @Schema(description = "Summary statistics")
    public record Summary(
        @JsonProperty("stored")
        @Schema(description = "Number of successfully stored events", example = "2")
        long stored,

        @JsonProperty("duplicate")
        @Schema(description = "Number of duplicate events", example = "1")
        long duplicate,

        @JsonProperty("invalid")
        @Schema(description = "Number of invalid events", example = "0")
        long invalid
    ) {}

    @Schema(description = "Result for a single event")
    public record EventResult(
        @JsonProperty("index")
        @Schema(description = "Event index in request", example = "0")
        int index,

        @JsonProperty("status")
        @Schema(description = "Event status", example = "stored")
        String status,

        @JsonProperty("eventId")
        @Schema(description = "Generated event ID", example = "evt_abc123", nullable = true)
        String eventId,

        @JsonProperty("error")
        @Schema(description = "Error details if invalid", nullable = true)
        ErrorDetail error
    ) {}

    @Schema(description = "Error details")
    public record ErrorDetail(
        @JsonProperty("field")
        @Schema(description = "Field that caused the error", example = "payload")
        String field,

        @JsonProperty("message")
        @Schema(description = "Error message", example = "Missing required field: workoutId")
        String message
    ) {}
}
