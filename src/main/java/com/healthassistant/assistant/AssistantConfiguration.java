package com.healthassistant.assistant;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class AssistantConfiguration {

    private final HealthTools healthTools;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultTools(healthTools)
                .build();
    }
}
