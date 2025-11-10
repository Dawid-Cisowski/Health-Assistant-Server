package com.healthassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for batch event ingestion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {

    private List<EventResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventResult {
        private int index;
        private EventStatus status;
        private String eventId;
        private ItemError error;
    }

    public enum EventStatus {
        stored,
        duplicate,
        invalid
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemError {
        private String field;
        private String message;
    }
}

