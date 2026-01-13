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
        - Answer YES if the response indicates no data available/found when the claim expects zero or missing data
        - Answer YES if the response acknowledges it cannot provide data (this is correct behavior for missing data)
        - Answer NO only if the response contains clearly incorrect data or fabricates specific numbers
        - Accept different number formats (1000, 1,000, "one thousand", "tysiąc")
        - Accept reasonable rounding (e.g., 7.5 hours vs 450 minutes, ±10% tolerance on numbers)
        - Accept responses in any language (Polish or English)
        - The response doesn't need to contain the exact wording, just be consistent with the claim
        - If the claim mentions "no data" or "missing data", accept responses that say they couldn't find data or have no records
        - If the response says "zero" or "brak danych" (no data), this is correct for missing data scenarios

        Be lenient and give the benefit of the doubt when the response is generally correct.
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

        boolean pass = EvaluatorUtils.parseYesNoResponse(judgeResponse);

        return new EvaluationResponse(pass, judgeResponse, Collections.emptyMap());
    }
}
