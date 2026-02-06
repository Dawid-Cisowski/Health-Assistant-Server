package com.healthassistant.assistant.advisor;

import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatGuardrailAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int ORDER = 0;
    private static final String NAME = "ChatGuardrailAdvisor";

    private final GuardrailFacade guardrailFacade;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        GuardrailResult result = validateRequest(request);

        if (result.blocked()) {
            log.info("Request blocked by guardrail: {}", result.internalReason());
            return createBlockedResponse(result.userMessage());
        }

        return chain.nextCall(request);
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request, @NonNull StreamAdvisorChain chain) {
        GuardrailResult result = validateRequest(request);

        if (result.blocked()) {
            log.info("Stream request blocked by guardrail: {}", result.internalReason());
            return Flux.just(createBlockedResponse(result.userMessage()));
        }

        return chain.nextStream(request);
    }

    private GuardrailResult validateRequest(ChatClientRequest request) {
        String userMessage = extractUserMessage(request);

        if (userMessage == null) {
            return GuardrailResult.allowed();
        }

        return guardrailFacade.validateText(userMessage, GuardrailProfile.CHAT);
    }

    private String extractUserMessage(ChatClientRequest request) {
        if (request == null) {
            return null;
        }

        List<Message> messages = request.prompt().getInstructions();
        if (messages.isEmpty()) {
            return null;
        }

        return messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(Message::getText)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private ChatClientResponse createBlockedResponse(String userMessage) {
        var generation = new Generation(
                new org.springframework.ai.chat.messages.AssistantMessage(userMessage)
        );
        var chatResponse = ChatResponse.builder()
                .generations(List.of(generation))
                .build();

        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();
    }
}
