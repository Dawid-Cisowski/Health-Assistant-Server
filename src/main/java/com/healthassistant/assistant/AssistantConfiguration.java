package com.healthassistant.assistant;

import com.healthassistant.assistant.advisor.ChatGuardrailAdvisor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class AssistantConfiguration {

    private final ChatGuardrailAdvisor chatGuardrailAdvisor;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(chatGuardrailAdvisor)
                .build();
    }
}
