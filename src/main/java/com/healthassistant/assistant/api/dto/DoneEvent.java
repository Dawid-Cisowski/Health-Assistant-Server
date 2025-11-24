package com.healthassistant.assistant.api.dto;

import java.util.UUID;

public record DoneEvent(UUID conversationId) implements AssistantEvent {
    @Override
    public String type() {
        return "done";
    }
}
