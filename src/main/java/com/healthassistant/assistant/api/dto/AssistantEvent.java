package com.healthassistant.assistant.api.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentEvent.class, name = "content"),
        @JsonSubTypes.Type(value = ToolCallEvent.class, name = "tool_call"),
        @JsonSubTypes.Type(value = ToolResultEvent.class, name = "tool_result"),
        @JsonSubTypes.Type(value = DoneEvent.class, name = "done"),
        @JsonSubTypes.Type(value = ErrorEvent.class, name = "error")
})
public sealed interface AssistantEvent permits ContentEvent, ToolCallEvent, ToolResultEvent, DoneEvent, ErrorEvent {
    String type();
}
