package com.healthassistant.assistant.api.dto;

public record ToolCallEvent(String toolName, Object toolArgs) implements AssistantEvent {
    @Override
    public String type() {
        return "tool_call";
    }
}
