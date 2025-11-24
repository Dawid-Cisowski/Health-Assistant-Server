package com.healthassistant.assistant.api;

import com.healthassistant.assistant.api.dto.AssistantEvent;
import com.healthassistant.assistant.api.dto.ChatRequest;
import reactor.core.publisher.Flux;

public interface AssistantFacade {

    Flux<AssistantEvent> streamChat(ChatRequest request, String deviceId);

}
