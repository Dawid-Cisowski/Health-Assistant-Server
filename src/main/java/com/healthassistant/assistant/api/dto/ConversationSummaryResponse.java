package com.healthassistant.assistant.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID conversationId,
        Instant createdAt,
        Instant updatedAt,
        String preview,
        int messageCount
) {}
