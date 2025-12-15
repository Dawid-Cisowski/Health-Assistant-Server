package com.healthassistant

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Flux

import java.util.concurrent.atomic.AtomicReference

@TestConfiguration
class TestChatModelConfiguration {

    // Shared state using AtomicReference for thread-safe cross-thread access
    private static final AtomicReference<String> CONFIGURED_RESPONSE = new AtomicReference<>("Test response")

    static void setResponse(String response) {
        CONFIGURED_RESPONSE.set(response)
    }

    static void resetResponse() {
        CONFIGURED_RESPONSE.set("Test response")
    }

    @Bean
    @Primary
    ChatModel testChatModel() {
        return new ChatModel() {
            @Override
            ChatResponse call(Prompt prompt) {
                String response = CONFIGURED_RESPONSE.get()
                return new ChatResponse([new Generation(new AssistantMessage(response))])
            }

            @Override
            Flux<ChatResponse> stream(Prompt prompt) {
                String response = CONFIGURED_RESPONSE.get()
                return Flux.just(
                        new ChatResponse([new Generation(new AssistantMessage(response))])
                )
            }
        }
    }
}
