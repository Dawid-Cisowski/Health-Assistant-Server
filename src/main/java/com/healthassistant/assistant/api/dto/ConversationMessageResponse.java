package com.healthassistant.assistant.api.dto;

import java.time.Instant;

public record ConversationMessageResponse(
        String role,
        String content,
        Instant createdAt
) {}
