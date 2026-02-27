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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestConfiguration
class TestChatModelConfiguration {

    // Shared state using AtomicReference for thread-safe cross-thread access
    private static final AtomicReference<String> CONFIGURED_RESPONSE = new AtomicReference<>("Test response")
    private static final AtomicInteger CALL_COUNT = new AtomicInteger(0)
    private static final java.util.concurrent.atomic.AtomicBoolean SHOULD_THROW = new java.util.concurrent.atomic.AtomicBoolean(false)

    static void setResponse(String response) {
        CONFIGURED_RESPONSE.set(response)
    }

    static void setThrowOnAllCalls() {
        SHOULD_THROW.set(true)
    }

    static void resetResponse() {
        CONFIGURED_RESPONSE.set("Test response")
        CALL_COUNT.set(0)
        SHOULD_THROW.set(false)
    }

    static int getCallCount() {
        return CALL_COUNT.get()
    }

    static void resetCallCount() {
        CALL_COUNT.set(0)
    }

    @Bean
    @Primary
    ChatModel testChatModel() {
        return new ChatModel() {
            @Override
            ChatResponse call(Prompt prompt) {
                CALL_COUNT.incrementAndGet()
                if (SHOULD_THROW.get()) {
                    throw new RuntimeException("Simulated AI failure for testing graceful degradation")
                }
                String response = CONFIGURED_RESPONSE.get()
                return new ChatResponse([new Generation(new AssistantMessage(response))])
            }

            @Override
            Flux<ChatResponse> stream(Prompt prompt) {
                CALL_COUNT.incrementAndGet()
                if (SHOULD_THROW.get()) {
                    throw new RuntimeException("Simulated AI failure for testing graceful degradation")
                }
                String response = CONFIGURED_RESPONSE.get()
                String[] words = response.split("(?<=\\s)")
                if (words.length <= 1) {
                    return Flux.just(
                            new ChatResponse([new Generation(new AssistantMessage(response))])
                    )
                }
                return Flux.fromArray(words)
                        .map { word -> new ChatResponse([new Generation(new AssistantMessage(word))]) }
            }
        }
    }
}
