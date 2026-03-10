package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI evaluation tests for medical exam read tools in the assistant.
 *
 * Verifies that the AI assistant can:
 * - List medical examinations via getMedicalExams tool
 * - Retrieve specific lab values via getMedicalExamDetail tool
 * - Show marker trends via getMarkerTrend tool
 * - Show health pillars via getHealthPillars tool
 * - Handle "no data" gracefully without hallucinating results
 *
 * Uses real Gemini API with LLM-as-a-Judge evaluation.
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=key ./gradlew :integration-tests:evaluationTest --tests "*AiMedicalExamToolsEvalSpec"
 */
@Title("AI Evaluation: Medical Exam Tools")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiMedicalExamToolsEvalSpec extends BaseEvaluationSpec {

    // ==================== E-EXAM-TOOLS-01: List exams ====================

    def "E-EXAM-TOOLS-01: AI lists blood morphology examination when asked in Polish"() {
        given: "a morphology examination exists from last week"
        def examDate = LocalDate.now().minusDays(7)
        seedExamination("MORPHOLOGY", "Morfologia krwi", examDate, "Laboratorium Centralne")

        when: "asking the AI to show blood tests"
        def response = askAssistant("Pokaż mi moje ostatnie badania krwi")
        println "DEBUG: AI response: $response"

        then: "AI mentions morphology or blood test in the response"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "User has a blood morphology exam (Morfologia krwi) performed ${examDate}",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== E-EXAM-TOOLS-02: Specific lab value ====================

    def "E-EXAM-TOOLS-02: AI reports correct WBC value from morphology exam"() {
        given: "a morphology examination with WBC=6.5 exists"
        def examDate = LocalDate.now().minusDays(5)
        def examId = seedExamination("MORPHOLOGY", "Morfologia krwi", examDate)
        seedLabResult(examId, "WBC", "Leukocyty", new BigDecimal("6.5"), "10^3/µL",
                new BigDecimal("4.0"), new BigDecimal("10.0"))

        when: "asking the AI about WBC value"
        def response = askAssistant("Jaki mam wynik WBC w ostatnim badaniu morfologii?")
        println "DEBUG: AI response: $response"

        then: "AI mentions the value 6.5 in its response"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "User's WBC (leukocyte) value is 6.5 in their latest morphology exam",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== E-EXAM-TOOLS-03: Health pillars ====================

    def "E-EXAM-TOOLS-03: AI shows health pillars when asked"() {
        when: "asking the AI for health pillars overview"
        def response = askAssistant("Pokaż mi moje filary zdrowia")
        println "DEBUG: AI response: $response"

        then: "AI returns health pillars info (mentions at least one pillar)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "The response shows health pillars dashboard. " +
                        "Health pillars include areas like CIRCULATORY (układ krążenia), BLOOD_IMMUNITY (krew i odporność), " +
                        "METABOLISM (metabolizm), DIGESTIVE (układ pokarmowy), VITAMINS_MINERALS (witaminy i minerały).",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== E-EXAM-TOOLS-04: Marker trend ====================

    def "E-EXAM-TOOLS-04: AI shows cholesterol trend across two exams"() {
        given: "two LIPID_PANEL exams with CHOLESTEROL_TOTAL values"
        def olderDate = LocalDate.now().minusDays(60)
        def newerDate = LocalDate.now().minusDays(10)

        def oldExamId = seedExamination("LIPID_PANEL", "Lipidogram (stary)", olderDate)
        seedLabResult(oldExamId, "CHOLESTEROL_TOTAL", "Cholesterol całkowity",
                new BigDecimal("195"), "mg/dL", new BigDecimal("0"), new BigDecimal("200"))

        def newExamId = seedExamination("LIPID_PANEL", "Lipidogram (nowy)", newerDate)
        seedLabResult(newExamId, "CHOLESTEROL_TOTAL", "Cholesterol całkowity",
                new BigDecimal("215"), "mg/dL", new BigDecimal("0"), new BigDecimal("200"))

        when: "asking the AI if cholesterol is increasing"
        def response = askAssistant("Czy mój cholesterol rośnie? Pokaż mi trend.")
        println "DEBUG: AI response: $response"

        then: "AI confirms cholesterol went up from 195 to 215"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "User's total cholesterol increased from 195 mg/dL to 215 mg/dL across two lipid panel tests",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== E-EXAM-TOOLS-05: No data — no hallucination ====================

    def "E-EXAM-TOOLS-05: AI does not hallucinate exams when no data exists"() {
        given: "no medical exams in the database (clean state from setup)"

        when: "asking the AI about medical results"
        def response = askAssistant("Jakie mam ostatnie wyniki badań laboratoryjnych?")
        println "DEBUG: AI response: $response"

        then: "AI admits it has no data, does not make up results"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "The user has no recorded medical exams or lab results. " +
                        "The AI should say there are no exams, no results, or no data found. " +
                        "The AI must NOT invent or guess any specific lab values.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== E-EXAM-TOOLS-06: Exam type listing ====================

    def "E-EXAM-TOOLS-06: AI lists available exam types when user asks what exams can be added"() {
        when: "asking the AI what exam types are available"
        def response = askAssistant("Jakie rodzaje badań możesz obsługiwać? Podaj kilka przykładów.")
        println "DEBUG: AI response: $response"

        then: "AI mentions at least one known exam type code or name"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "The AI lists or mentions medical exam types. " +
                        "Examples include: MORPHOLOGY (morfologia), LIPID_PANEL (lipidogram), TSH, GLUCOSE (glukoza), " +
                        "or other lab test types. The response should include at least one specific exam type.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }
}
