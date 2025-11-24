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

@TestConfiguration
class TestChatModelConfiguration {

    @Bean
    @Primary
    ChatModel testChatModel() {
        return new ChatModel() {
            @Override
            ChatResponse call(Prompt prompt) {
                return new ChatResponse([new Generation(new AssistantMessage("Test response"))])
            }

            @Override
            Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(
                        new ChatResponse([new Generation(new AssistantMessage("Test"))]),
                        new ChatResponse([new Generation(new AssistantMessage(" response"))])
                )
            }
        }
    }
}
