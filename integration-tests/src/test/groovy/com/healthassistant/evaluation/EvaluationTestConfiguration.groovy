package com.healthassistant.evaluation

import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile

/**
 * Test configuration for AI evaluation tests.
 *
 * This configuration is only active with the 'evaluation' profile and provides
 * the HealthDataEvaluator bean for LLM-as-a-Judge testing.
 *
 * IMPORTANT: This configuration does NOT mock the ChatModel - it uses the real
 * Gemini API to enable proper evaluation of AI responses.
 */
@TestConfiguration
@Profile("evaluation")
class EvaluationTestConfiguration {

    @Bean
    HealthDataEvaluator healthDataEvaluator(ChatClient.Builder chatClientBuilder) {
        return new HealthDataEvaluator(chatClientBuilder)
    }

    @Bean
    PromptInjectionEvaluator promptInjectionEvaluator(ChatClient.Builder chatClientBuilder) {
        return new PromptInjectionEvaluator(chatClientBuilder)
    }

    @Bean
    ContentFilteringEvaluator contentFilteringEvaluator(ChatClient.Builder chatClientBuilder) {
        return new ContentFilteringEvaluator(chatClientBuilder)
    }

    @Bean
    DailySummaryEvaluator dailySummaryEvaluator(ChatClient.Builder chatClientBuilder) {
        return new DailySummaryEvaluator(chatClientBuilder)
    }
}
