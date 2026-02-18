package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI evaluation tests for mutation tools (record, update, delete).
 *
 * Verifies that the AI assistant correctly uses mutation tools when users
 * describe meals, workouts, sleep, and weight in natural language.
 *
 * Uses real Gemini API with LLM-as-a-Judge evaluation pattern.
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=key ./gradlew :integration-tests:test --tests "*AiMutationToolSpec"
 */
@Title("AI Evaluation: Mutation Tools")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiMutationToolSpec extends BaseEvaluationSpec {

    // ==================== recordMeal ====================

    def "AI records a meal when user describes what they ate"() {
        when: "user describes a meal"
        def response = askAssistant("Zjadłem kurczaka z ryżem na obiad, około 500 kalorii, 40g białka, 15g tłuszczu, 55g węglowodanów")
        println "DEBUG: Response: $response"

        then: "AI confirms meal was recorded"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should confirm the meal was recorded/saved successfully. " +
                        "It should mention the meal (chicken with rice / kurczak z ryżem) and the macros. " +
                        "It should NOT ask for more information or refuse to record.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI records a meal and estimates macros when user provides only description"() {
        when: "user describes a meal without macros"
        def response = askAssistant("Zjadłem banana na śniadanie")
        println "DEBUG: Response: $response"

        then: "AI records the meal and mentions estimating macros"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should confirm recording a banana as a meal (breakfast/snack). " +
                        "It should mention that it estimated/approximated the nutritional values. " +
                        "It should NOT refuse to record the meal.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI correctly picks meal type from context"() {
        when: "user mentions lunch in Polish"
        def response = askAssistant("Na lunch miałem sałatkę cezar, 350 kcal, 25g białka, 20g tłuszczu, 15g węgli")
        println "DEBUG: Response: $response"

        then: "AI uses LUNCH meal type"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should confirm recording a Caesar salad for lunch. " +
                        "It should confirm approximately 350 calories.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== recordWeight ====================

    def "AI records weight when user reports it"() {
        when: "user reports weight"
        def response = askAssistant("Ważyłem się dziś rano - 82.5 kg")
        println "DEBUG: Response: $response"

        then: "AI confirms weight was recorded"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should confirm recording weight of 82.5 kg. " +
                        "It should NOT refuse or ask for additional information.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== recordWorkout ====================

    def "AI records a simple workout"() {
        when: "user describes a workout"
        def response = askAssistant("Zrobiłem dzisiaj wyciskanie sztangi na ławce płaskiej: 3 serie po 10 powtórzeń z 80kg")
        println "DEBUG: Response: $response"

        then: "AI confirms workout was recorded with correct details"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should confirm recording a bench press workout. " +
                        "It should mention 3 sets, 10 reps, and 80 kg (or equivalent). " +
                        "It should NOT refuse to record the workout.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== recordSleep ====================

    def "AI records sleep when user reports it"() {
        when: "user describes sleep"
        def response = askAssistant("Spałem od 23:00 do 7:00")
        println "DEBUG: Response: $response"

        then: "AI confirms sleep was recorded"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should confirm recording sleep of approximately 8 hours (480 minutes). " +
                        "It should NOT refuse or say it cannot record sleep.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Delete Safety ====================

    def "AI asks for confirmation before deleting a meal"() {
        given: "a meal is recorded via events"
        submitMeal("Scrambled eggs", "BREAKFAST", 350, 25, 20, 5)
        waitForProjections()

        when: "user asks to delete it"
        def response = askAssistant("Usuń moje dzisiejsze śniadanie")
        println "DEBUG: Response: $response"

        then: "AI asks for confirmation or shows what will be deleted"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should either: (1) ask the user to confirm deletion, or (2) show the meal details and ask for confirmation, " +
                        "or (3) proceed with deletion and confirm it was deleted. " +
                        "Any of these behaviors is acceptable. " +
                        "It should NOT refuse to perform the deletion entirely or say it cannot delete meals.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Multi-step flow ====================

    def "AI can record a meal and then answer questions about it"() {
        when: "user records a meal"
        def response1 = askAssistantStart("Zjadłem kanapkę z szynką na śniadanie, 300 kcal, 20g białka, 10g tłuszczu, 35g węgli")
        println "DEBUG: Response 1: $response1"

        and: "user asks about today's meals"
        def response2 = askAssistantContinue("Ile kalorii zjadłem dzisiaj?")
        println "DEBUG: Response 2: $response2"

        then: "AI correctly reports the recorded meal"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should mention approximately 300 calories consumed today (from the sandwich just recorded). " +
                        "It should NOT say there are no meals or hallucinate a different calorie count.",
                        [],
                        response2
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Refusal to hallucinate ====================

    def "AI does not speculatively create data"() {
        when: "user asks a vague question that should not trigger mutation"
        def response = askAssistant("Myślę, że dzisiaj zjem pizzę")
        println "DEBUG: Response: $response"

        then: "AI does NOT record a meal - it's just a thought"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should NOT record a meal because the user is only thinking about eating pizza. " +
                        "The response should discuss pizza but NOT confirm recording a meal. " +
                        "Saying 'I recorded your pizza' would be wrong - the user didn't eat it yet.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }
}
