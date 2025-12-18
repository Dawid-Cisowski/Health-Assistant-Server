package com.healthassistant.evaluation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.Collections;

/**
 * Custom evaluator that uses LLM-as-a-Judge pattern to verify AI responses
 * contain factually correct health data without hallucinations.
 *
 * This evaluator compares the AI assistant's response against a known claim
 * (golden data) and determines if the response is factually consistent.
 */
public class HealthDataEvaluator implements Evaluator {

    private static final String EVALUATION_PROMPT = """
        Your task is to evaluate if the assistant's response is factually correct.

        CLAIM (expected fact): {claim}
        ASSISTANT RESPONSE: {response}

        Rules:
        - Answer YES if the response contains information consistent with the claim
        - Answer NO if the response contains incorrect data, made-up numbers, or hallucination
        - Accept different number formats (1000, 1,000, "one thousand", "tysiąc")
        - Accept reasonable rounding (e.g., 7.5 hours vs 450 minutes)
        - The response doesn't need to contain the exact wording, just the correct data

        Respond with only YES or NO, followed by a brief explanation.
        """;

    private final ChatClient chatClient;

    public HealthDataEvaluator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        String judgeResponse = chatClient.prompt()
            .user(u -> u.text(EVALUATION_PROMPT)
                .param("claim", request.getUserText())
                .param("response", request.getResponseContent()))
            .call()
            .content();

        boolean pass = judgeResponse != null
            && judgeResponse.trim().toUpperCase().startsWith("YES");

        return new EvaluationResponse(pass, judgeResponse, Collections.emptyMap());
    }
}
