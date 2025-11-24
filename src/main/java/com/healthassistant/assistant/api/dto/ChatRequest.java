package com.healthassistant.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 1000, message = "Message too long (max 1000 characters)")
        String message
) {
}
