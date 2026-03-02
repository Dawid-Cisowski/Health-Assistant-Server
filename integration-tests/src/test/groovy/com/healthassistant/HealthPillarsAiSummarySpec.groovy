package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Health Pillars AI Insight Caching")
class HealthPillarsAiSummarySpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-hp-ai"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String DIFF_DEVICE_ID = "test-hp-ai-d2"
    private static final String BASE_PATH = "/v1/medical-exams"
    private static final String PILLARS_PATH = "/v1/medical-exams/health-pillars"

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DIFF_DEVICE_ID)
        jdbcTemplate.update("DELETE FROM health_pillar_ai_summaries WHERE device_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM health_pillar_ai_summaries WHERE device_id = ?", DIFF_DEVICE_ID)
        TestChatModelConfiguration.resetResponse()
        TestChatModelConfiguration.resetCallCount()
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    // ===================== Scenario 1: No AI call when no lab data =====================

    def "should return AI insight from mock even when no lab data exists"() {
        when: "I fetch health pillars with no lab data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "overallAiInsight is generated (AI is called with empty context and returns mocked response)"
        response.body().jsonPath().getString("overallAiInsight") != null
    }

    // ===================== Scenario 2: AI insight generated on first request =====================

    def "should generate AI insight on first request when lab data exists"() {
        given: "lab data exists for the device"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "HGB", "Hemoglobina", 14.0, 12.0, 16.0)

        and: "AI is configured to return a specific response"
        TestChatModelConfiguration.setResponse("Ogólny stan zdrowia jest dobry - generacja pierwsza")
        TestChatModelConfiguration.resetCallCount()

        when: "I fetch health pillars"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "overall AI insight is populated"
        def insight = response.body().jsonPath().getString("overallAiInsight")
        insight != null
        insight == "Ogólny stan zdrowia jest dobry - generacja pierwsza"

        and: "AI was called at least once"
        TestChatModelConfiguration.getCallCount() >= 1
    }

    // ===================== Scenario 3: Cache is served on second request =====================

    def "should return cached insight on second request without new AI call"() {
        given: "lab data exists"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL", 100.0, 0.0, 130.0)

        and: "first request generates the insight"
        TestChatModelConfiguration.setResponse("podsumowanie pierwsze - cache test")
        TestChatModelConfiguration.resetCallCount()

        def firstResponse = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        def callCountAfterFirst = TestChatModelConfiguration.getCallCount()
        def firstInsight = firstResponse.body().jsonPath().getString("overallAiInsight")

        expect: "first call returned an insight"
        firstInsight != null
        callCountAfterFirst >= 1

        when: "AI response changes but no new lab data is added"
        TestChatModelConfiguration.setResponse("podsumowanie drugie - NIE POWINNO BYC WIDOCZNE")

        def secondResponse = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "same cached insight is returned"
        secondResponse.statusCode() == 200
        secondResponse.body().jsonPath().getString("overallAiInsight") == firstInsight

        and: "AI was not called again"
        TestChatModelConfiguration.getCallCount() == callCountAfterFirst
    }

    // ===================== Scenario 4: Cache invalidated after addResults =====================

    def "should regenerate insight after new lab results are added"() {
        given: "exam with initial lab result"
        def examId = createExaminationOnDate(DEVICE_ID, SECRET, LocalDate.now())
        addSingleResult(DEVICE_ID, SECRET, examId, "HGB", "Hemoglobina", 14.0, 12.0, 16.0)

        and: "first insight is generated"
        TestChatModelConfiguration.setResponse("stare podsumowanie - przed nowym wynikiem")
        TestChatModelConfiguration.resetCallCount()

        def firstResponse = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()
        def firstInsight = firstResponse.body().jsonPath().getString("overallAiInsight")
        def callCountAfterFirst = TestChatModelConfiguration.getCallCount()

        expect: "first insight is generated"
        firstInsight != null

        when: "new lab results are added"
        addSingleResult(DEVICE_ID, SECRET, examId, "LDL", "LDL", 200.0, 0.0, 130.0)

        and: "AI returns a different response"
        TestChatModelConfiguration.setResponse("nowe podsumowanie - po dodaniu wyniku LDL")

        and: "health pillars are fetched again"
        def secondResponse = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "new insight is generated (cache was invalidated)"
        secondResponse.statusCode() == 200
        def secondInsight = secondResponse.body().jsonPath().getString("overallAiInsight")
        secondInsight != null
        secondInsight == "nowe podsumowanie - po dodaniu wyniku LDL"

        and: "AI was called again"
        TestChatModelConfiguration.getCallCount() > callCountAfterFirst
    }

    // ===================== Scenario 5: Cache invalidated after deleteResult =====================

    def "should regenerate insight after lab result is deleted"() {
        given: "exam with a lab result"
        def examId = createExaminationOnDate(DEVICE_ID, SECRET, LocalDate.now())
        addSingleResult(DEVICE_ID, SECRET, examId, "HGB", "Hemoglobina", 14.0, 12.0, 16.0)

        and: "first insight is generated"
        TestChatModelConfiguration.setResponse("podsumowanie z HGB")
        TestChatModelConfiguration.resetCallCount()

        authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        def callCountAfterFirst = TestChatModelConfiguration.getCallCount()

        when: "the lab result is deleted"
        def resultsResponse = authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .get("${BASE_PATH}/${examId}")
                .then()
                .extract()
        def resultId = resultsResponse.body().jsonPath().getList("results").first().id

        authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results/${resultId}")
                .delete("${BASE_PATH}/${examId}/results/${resultId}")
                .then()
                .statusCode(204)

        and: "AI returns a different response"
        TestChatModelConfiguration.setResponse("podsumowanie bez HGB - po usuniêciu")

        and: "health pillars are fetched"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "new insight is generated"
        response.statusCode() == 200
        response.body().jsonPath().getString("overallAiInsight") == "podsumowanie bez HGB - po usuniêciu"
        TestChatModelConfiguration.getCallCount() > callCountAfterFirst
    }

    // ===================== Scenario 6: Cache invalidated after deleteExamination =====================

    def "should regenerate insight after examination is deleted"() {
        given: "an examination with a lab result"
        def examId = createExaminationOnDate(DEVICE_ID, SECRET, LocalDate.now())
        addSingleResult(DEVICE_ID, SECRET, examId, "LDL", "LDL", 100.0, 0.0, 130.0)

        and: "first insight is generated"
        TestChatModelConfiguration.setResponse("podsumowanie z LDL")
        TestChatModelConfiguration.resetCallCount()

        authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        def callCountAfterFirst = TestChatModelConfiguration.getCallCount()

        when: "the examination is deleted"
        authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .delete("${BASE_PATH}/${examId}")
                .then()
                .statusCode(204)

        and: "AI returns a different response"
        TestChatModelConfiguration.setResponse("podsumowanie bez LDL - badanie usuniête")

        and: "health pillars are fetched"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "new insight is generated"
        response.statusCode() == 200
        response.body().jsonPath().getString("overallAiInsight") == "podsumowanie bez LDL - badanie usuniête"
        TestChatModelConfiguration.getCallCount() > callCountAfterFirst
    }

    // ===================== Scenario 7: Detail endpoint returns per-pillar aiInsight =====================

    def "should return per-pillar aiInsight in detail endpoint"() {
        given: "lab data exists for CIRCULATORY"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL", 100.0, 0.0, 130.0)

        and: "AI is configured"
        TestChatModelConfiguration.setResponse("Twój profil lipidowy jest prawidłowy")
        TestChatModelConfiguration.resetCallCount()

        when: "I fetch CIRCULATORY detail"
        def path = "${PILLARS_PATH}/CIRCULATORY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "aiInsight is populated"
        def insight = response.body().jsonPath().getString("aiInsight")
        insight != null
        insight == "Twój profil lipidowy jest prawidłowy"

        and: "AI was called at least once"
        TestChatModelConfiguration.getCallCount() >= 1
    }

    // ===================== Scenario 8: Cross-device isolation =====================

    def "should not serve insights from one device to another"() {
        given: "lab data and a cached insight for device 1"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "HGB", "Hemoglobina", 14.0, 12.0, 16.0)
        TestChatModelConfiguration.setResponse("podsumowanie dla urządzenia 1")
        TestChatModelConfiguration.resetCallCount()

        authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        and: "lab data for device 2"
        createExamWithResult(DIFF_DEVICE_ID, SECRET, LocalDate.now(), "HGB", "Hemoglobina", 9.0, 12.0, 16.0)

        when: "AI response changes and device 2 fetches"
        TestChatModelConfiguration.setResponse("podsumowanie dla urządzenia 2")
        def callCountBefore = TestChatModelConfiguration.getCallCount()

        def response2 = authenticatedGetRequest(DIFF_DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "device 2 gets its own insight (AI called again)"
        response2.statusCode() == 200
        def insight2 = response2.body().jsonPath().getString("overallAiInsight")
        insight2 == "podsumowanie dla urządzenia 2"
        TestChatModelConfiguration.getCallCount() > callCountBefore
    }

    // ===================== Scenario 9: AI failure → graceful degradation =====================

    def "should return 200 with null insights when AI throws an exception"() {
        given: "lab data exists"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "HGB", "Hemoglobina", 14.0, 12.0, 16.0)

        and: "AI is configured to throw"
        TestChatModelConfiguration.setThrowOnAllCalls()

        when: "I fetch health pillars"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "response is still 200"
        response.statusCode() == 200

        and: "overallAiInsight is null (graceful degradation)"
        response.body().jsonPath().get("overallAiInsight") == null

        and: "pillars are still returned"
        def pillars = response.body().jsonPath().getList("pillars")
        pillars.size() == 5
    }

    def "should return 200 with null aiInsight in detail when AI throws"() {
        given: "lab data exists"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL", 100.0, 0.0, 130.0)

        and: "AI is configured to throw"
        TestChatModelConfiguration.setThrowOnAllCalls()

        when: "I fetch CIRCULATORY detail"
        def path = "${PILLARS_PATH}/CIRCULATORY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response is still 200"
        response.statusCode() == 200

        and: "aiInsight is null (graceful degradation)"
        response.body().jsonPath().get("aiInsight") == null

        and: "pillar data is still returned"
        response.body().jsonPath().getString("pillarCode") == "CIRCULATORY"
    }

    // ===================== Helper methods =====================

    private String createExamWithResult(String deviceId, String secret, LocalDate date,
                                         String markerCode, String markerName,
                                         double value, Double refLow, Double refHigh) {
        def examId = createExaminationOnDate(deviceId, secret, date)
        addSingleResult(deviceId, secret, examId, markerCode, markerName, value, refLow, refHigh)
        return examId
    }

    private String createExaminationOnDate(String deviceId, String secret, LocalDate date) {
        def request = """
        {
            "examTypeCode": "MORPHOLOGY",
            "title": "Health pillar AI test exam",
            "date": "${date}"
        }
        """
        def response = authenticatedPostRequestWithBody(deviceId, secret, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .statusCode(201)
                .extract()
        return response.body().jsonPath().getString("id")
    }

    private void addSingleResult(String deviceId, String secret, String examId,
                                  String markerCode, String markerName,
                                  double value, Double refLow, Double refHigh) {
        def refLowJson = refLow != null ? refLow.toString() : "null"
        def refHighJson = refHigh != null ? refHigh.toString() : "null"
        def request = """
        {
            "results": [
                {
                    "markerCode": "${markerCode}",
                    "markerName": "${markerName}",
                    "category": "LAB",
                    "valueNumeric": ${value},
                    "refRangeLow": ${refLowJson},
                    "refRangeHigh": ${refHighJson},
                    "sortOrder": 1
                }
            ]
        }
        """
        authenticatedPostRequestWithBody(deviceId, secret, "${BASE_PATH}/${examId}/results", request)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .statusCode(201)
    }

}
