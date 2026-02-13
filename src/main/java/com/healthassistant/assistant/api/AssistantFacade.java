package com.healthassistant.assistant.api;

import com.healthassistant.assistant.api.dto.AssistantEvent;
import com.healthassistant.assistant.api.dto.ChatRequest;
import com.healthassistant.assistant.api.dto.ConversationDetailResponse;
import com.healthassistant.assistant.api.dto.ConversationSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.UUID;

public interface AssistantFacade {

    Flux<AssistantEvent> streamChat(ChatRequest request, String deviceId);

    Page<ConversationSummaryResponse> listConversations(String deviceId, Pageable pageable);

    Optional<ConversationDetailResponse> getConversationDetail(UUID conversationId, String deviceId);

    void deleteConversationsByDeviceId(String deviceId);

}
