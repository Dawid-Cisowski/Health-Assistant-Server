package com.healthassistant.evaluation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.Collections;

/**
 * Custom evaluator that uses LLM-as-a-Judge pattern to verify AI-generated
 * daily summaries are accurate, helpful, and appropriately formatted.
 *
 * This evaluator checks that the AI:
 * - Accurately reports health data within tolerance
 * - Acknowledges missing data appropriately
 * - Uses friendly, supportive tone
 * - Writes grammatically correct Polish (if applicable)
 * - Does not hallucinate data that wasn't provided
 */
public class DailySummaryEvaluator implements Evaluator {

    private static final String EVALUATION_PROMPT = """
        Your task is to evaluate if the AI daily summary is accurate and high quality.

        ACTUAL DATA (ground truth): {actualData}
        AI SUMMARY: {summary}

        Evaluate these criteria:

        1. ACCURACY (most important):
           - All mentioned numbers should be within 10% of actual data
           - If data says "5000 steps", summary should not say "6000 steps"
           - Minor rounding is acceptable (7.5 hours vs 450 minutes)

        2. COMPLETENESS:
           - Available data types should be mentioned
           - Missing data should be acknowledged, not invented
           - Don't expect summary to mention data that doesn't exist

        3. TONE:
           - Should be friendly and supportive
           - Should NOT be clinical or robotic
           - Should NOT be judgmental about low performance
           - Encouragement is good

        4. NO HALLUCINATION:
           - Summary should NOT invent specific numbers not in the data
           - If no sleep data provided, should not say "you slept 8 hours"
           - If no workout data, should not mention specific exercises
           - EXCEPTION: The AI knows user profile: Male, 21yo, 178cm, 73kg, goals: muscle building.
             Mentioning these is NOT hallucination - it's using known context.

        5. LANGUAGE QUALITY (if in Polish):
           - Should be grammatically correct
           - Should use appropriate health terminology

        Based on these criteria, answer YES if the summary meets quality standards,
        NO if there are significant issues.

        Respond with YES or NO followed by a brief explanation.
        Be lenient on minor issues - focus on accuracy and helpfulness.
        """;

    private final ChatClient chatClient;

    public DailySummaryEvaluator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        String judgeResponse = chatClient.prompt()
            .user(u -> u.text(EVALUATION_PROMPT)
                .param("actualData", request.getUserText())
                .param("summary", request.getResponseContent()))
            .call()
            .content();

        boolean pass = EvaluatorUtils.parseYesNoResponse(judgeResponse);

        return new EvaluationResponse(pass, judgeResponse, Collections.emptyMap());
    }
}
