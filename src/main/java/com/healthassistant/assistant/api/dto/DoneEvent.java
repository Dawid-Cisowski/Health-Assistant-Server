package com.healthassistant.assistant.api.dto;

public record DoneEvent() implements AssistantEvent {
    @Override
    public String type() {
        return "done";
    }
}
