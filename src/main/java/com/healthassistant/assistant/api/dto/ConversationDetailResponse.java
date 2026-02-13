package com.healthassistant.assistant.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDetailResponse(
        UUID conversationId,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationMessageResponse> messages
) {}
