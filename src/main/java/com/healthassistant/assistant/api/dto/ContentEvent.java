package com.healthassistant.assistant.api.dto;

public record ContentEvent(String content) implements AssistantEvent {
    @Override
    public String type() {
        return "content";
    }
}
