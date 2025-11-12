package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.EventId;

import java.util.List;

public record StoreHealthEventsResult(List<EventResult> results) {
    
    public record EventResult(
        int index,
        EventStatus status,
        EventId eventId,
        EventError error
    ) {}
    
    public enum EventStatus {
        stored,
        duplicate,
        invalid
    }
    
    public record EventError(String field, String message) {}
}
