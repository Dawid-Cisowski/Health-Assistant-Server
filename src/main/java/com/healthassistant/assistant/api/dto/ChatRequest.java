package com.healthassistant.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatRequest(
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 4000, message = "Message too long (max 4000 characters)")
        String message,

        UUID conversationId
) {
}
