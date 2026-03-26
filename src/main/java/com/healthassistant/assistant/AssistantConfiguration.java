package com.healthassistant.assistant;

import com.healthassistant.assistant.advisor.ChatGuardrailAdvisor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
class AssistantConfiguration {

    private final ChatGuardrailAdvisor chatGuardrailAdvisor;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(chatGuardrailAdvisor)
                .build();
    }

    @Bean
    ToolCallAdvisor toolCallAdvisor() {
        return ToolCallAdvisor.builder().build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
    ToolCallbackProvider healthMcpTools(@Lazy HealthTools healthTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(healthTools)
                .build();
    }
}
