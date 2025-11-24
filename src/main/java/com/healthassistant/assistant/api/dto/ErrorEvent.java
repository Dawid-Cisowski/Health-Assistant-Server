package com.healthassistant.assistant.api.dto;

public record ErrorEvent(String content) implements AssistantEvent {
    @Override
    public String type() {
        return "error";
    }
}
