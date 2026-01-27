package com.healthassistant.assistant.api.dto;

import java.util.UUID;

public record DoneEvent(
        UUID conversationId,
        Long promptTokens,
        Long completionTokens
) implements AssistantEvent {

    public DoneEvent(UUID conversationId) {
        this(conversationId, null, null);
    }

    @Override
    public String type() {
        return "done";
    }
}
