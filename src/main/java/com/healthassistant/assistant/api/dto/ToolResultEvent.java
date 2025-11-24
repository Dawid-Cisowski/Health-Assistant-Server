package com.healthassistant.assistant.api.dto;

public record ToolResultEvent(String toolName, Object toolResult) implements AssistantEvent {
    @Override
    public String type() {
        return "tool_result";
    }
}
