package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Health Pillars — imaging/endoscopy status markers (badania obrazowe i endoskopowe w filarach zdrowia)")
class HealthPillarsImagingSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-hp-imaging"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String BASE_PATH = "/v1/medical-exams"
    private static final String DIGESTIVE_PATH = "/v1/medical-exams/health-pillars/DIGESTIVE"
    private static final String CIRCULATORY_PATH = "/v1/medical-exams/health-pillars/CIRCULATORY"
    private static final String METABOLISM_PATH = "/v1/medical-exams/health-pillars/METABOLISM"

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM health_pillar_ai_summaries WHERE device_id = ?", DEVICE_ID)
    }

    // ===================== Scenario 1: No imaging data — GUT section has no *_OVERALL markers =====================

    def "should not show GASTROSCOPY_OVERALL in GUT section when no imaging exams exist"() {
        when: "I fetch DIGESTIVE detail with no data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, DIGESTIVE_PATH)
                .get(DIGESTIVE_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "GUT section does not contain GASTROSCOPY_OVERALL marker"
        def sections = response.body().jsonPath().getList("sections")
        def gutSection = sections.find { it.sectionCode == "GUT" }
        // GUT section may be absent (no data) or present with lab markers only
        gutSection == null || !gutSection.markers.any { it.markerCode == "GASTROSCOPY_OVERALL" }
    }

    // ===================== Scenario 2: Gastroscopy OK (value=2) → NORMAL, score=100 =====================

    def "should show GASTROSCOPY_OVERALL in GUT section with NORMAL flag and score 100 when value=2 (OK)"() {
        given: "Gastroscopy exam with GASTROSCOPY_OVERALL=2 (normal findings)"
        def examId = createImagingExam(DEVICE_ID, SECRET, "GASTROSCOPY", "Gastroskopia", LocalDate.now())
        addImagingOverallResult(DEVICE_ID, SECRET, examId, "GASTROSCOPY_OVERALL", "Wynik gastroskopii", "GASTROSCOPY", 2.0, "Prawidłowe")

        when: "I fetch DIGESTIVE detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, DIGESTIVE_PATH)
                .get(DIGESTIVE_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "GUT section is present and contains GASTROSCOPY_OVERALL"
        def sections = response.body().jsonPath().getList("sections")
        def gutSection = sections.find { it.sectionCode == "GUT" }
        gutSection != null

        and: "GASTROSCOPY_OVERALL marker has NORMAL flag and score 100"
        def marker = gutSection.markers.find { it.markerCode == "GASTROSCOPY_OVERALL" }
        marker != null
        marker.flag == "NORMAL"
        marker.score == 100
        marker.valueText == "Prawidłowe"
    }

    // ===================== Scenario 3: Gastroscopy WARNING (value=1) → WARNING, score=75 =====================

    def "should show GASTROSCOPY_OVERALL with WARNING flag and score 75 when value=1 (minor findings)"() {
        given: "Gastroscopy exam with GASTROSCOPY_OVERALL=1 (minor/watchful findings)"
        def examId = createImagingExam(DEVICE_ID, SECRET, "GASTROSCOPY", "Gastroskopia", LocalDate.now())
        addImagingOverallResult(DEVICE_ID, SECRET, examId, "GASTROSCOPY_OVERALL", "Wynik gastroskopii", "GASTROSCOPY", 1.0, "Wymaga obserwacji")

        when: "I fetch DIGESTIVE detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, DIGESTIVE_PATH)
                .get(DIGESTIVE_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "GASTROSCOPY_OVERALL has WARNING flag and score 75"
        def sections = response.body().jsonPath().getList("sections")
        def gutSection = sections.find { it.sectionCode == "GUT" }
        gutSection != null
        def marker = gutSection.markers.find { it.markerCode == "GASTROSCOPY_OVERALL" }
        marker != null
        marker.flag == "WARNING"
        marker.score == 75
        marker.valueText == "Wymaga obserwacji"
    }

    // ===================== Scenario 4: Gastroscopy NOT_OK (value=0) → LOW, score=50 =====================

    def "should show GASTROSCOPY_OVERALL with LOW flag and score 50 when value=0 (pathological findings)"() {
        given: "Gastroscopy exam with GASTROSCOPY_OVERALL=0 (pathological/significant findings)"
        def examId = createImagingExam(DEVICE_ID, SECRET, "GASTROSCOPY", "Gastroskopia", LocalDate.now())
        addImagingOverallResult(DEVICE_ID, SECRET, examId, "GASTROSCOPY_OVERALL", "Wynik gastroskopii", "GASTROSCOPY", 0.0, "Nieprawidłowe")

        when: "I fetch DIGESTIVE detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, DIGESTIVE_PATH)
                .get(DIGESTIVE_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "GASTROSCOPY_OVERALL has LOW flag and score 50"
        def sections = response.body().jsonPath().getList("sections")
        def gutSection = sections.find { it.sectionCode == "GUT" }
        gutSection != null
        def marker = gutSection.markers.find { it.markerCode == "GASTROSCOPY_OVERALL" }
        marker != null
        marker.flag == "LOW"
        marker.score == 50
        marker.valueText == "Nieprawidłowe"
    }

    // ===================== Scenario 5: Echo OK (value=2) → ECHO_OVERALL in HEART_HEALTH, NORMAL, score=100 =====================

    def "should show ECHO_OVERALL in HEART_HEALTH section of CIRCULATORY with NORMAL flag and score 100 when value=2"() {
        given: "Echo exam with ECHO_OVERALL=2 (normal echocardiography)"
        def examId = createImagingExam(DEVICE_ID, SECRET, "ECHO", "Echo serca", LocalDate.now())
        addImagingOverallResult(DEVICE_ID, SECRET, examId, "ECHO_OVERALL", "Wynik echa serca", "ECHO", 2.0, "Prawidłowe")

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "HEART_HEALTH section is present and contains ECHO_OVERALL"
        def sections = response.body().jsonPath().getList("sections")
        def heartSection = sections.find { it.sectionCode == "HEART_HEALTH" }
        heartSection != null

        and: "ECHO_OVERALL has NORMAL flag and score 100"
        def marker = heartSection.markers.find { it.markerCode == "ECHO_OVERALL" }
        marker != null
        marker.flag == "NORMAL"
        marker.score == 100
    }

    // ===================== Scenario 6: USG thyroid WARNING → USG_THYROID_OVERALL in THYROID section of METABOLISM =====================

    def "should show USG_THYROID_OVERALL in THYROID section of METABOLISM with WARNING flag and score 75 when value=1"() {
        given: "Thyroid USG exam with USG_THYROID_OVERALL=1 (watchful findings)"
        def examId = createImagingExam(DEVICE_ID, SECRET, "THYROID_USG", "USG tarczycy", LocalDate.now())
        addImagingOverallResult(DEVICE_ID, SECRET, examId, "USG_THYROID_OVERALL", "Wynik USG tarczycy", "THYROID_USG", 1.0, "Wymaga obserwacji")

        when: "I fetch METABOLISM detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, METABOLISM_PATH)
                .get(METABOLISM_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "THYROID section is present and contains USG_THYROID_OVERALL"
        def sections = response.body().jsonPath().getList("sections")
        def thyroidSection = sections.find { it.sectionCode == "THYROID" }
        thyroidSection != null

        and: "USG_THYROID_OVERALL has WARNING flag and score 75"
        def marker = thyroidSection.markers.find { it.markerCode == "USG_THYROID_OVERALL" }
        marker != null
        marker.flag == "WARNING"
        marker.score == 75
    }

    // ===================== Scenario 7: Chest X-Ray OK (value=2) → CHEST_XRAY_OVERALL in new CHEST_IMAGING section =====================

    def "should show CHEST_XRAY_OVERALL in CHEST_IMAGING section of CIRCULATORY with NORMAL flag and score 100 when value=2"() {
        given: "Chest X-Ray exam with CHEST_XRAY_OVERALL=2 (normal findings)"
        def examId = createImagingExam(DEVICE_ID, SECRET, "CHEST_XRAY", "RTG klatki piersiowej", LocalDate.now())
        addImagingOverallResult(DEVICE_ID, SECRET, examId, "CHEST_XRAY_OVERALL", "Wynik RTG klatki piersiowej", "CHEST_XRAY", 2.0, "Prawidłowe")

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "CHEST_IMAGING section is present with correct name"
        def sections = response.body().jsonPath().getList("sections")
        def chestSection = sections.find { it.sectionCode == "CHEST_IMAGING" }
        chestSection != null
        chestSection.sectionNamePl == "RTG klatki piersiowej"

        and: "CHEST_XRAY_OVERALL has NORMAL flag and score 100"
        def marker = chestSection.markers.find { it.markerCode == "CHEST_XRAY_OVERALL" }
        marker != null
        marker.flag == "NORMAL"
        marker.score == 100
    }

    // ===================== Helper methods =====================

    private String createImagingExam(String deviceId, String secret, String examTypeCode, String title, LocalDate date) {
        def request = """{"examTypeCode": "${examTypeCode}", "title": "${title}", "date": "${date}"}"""
        def response = authenticatedPostRequestWithBody(deviceId, secret, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .statusCode(201)
                .extract()
        return response.body().jsonPath().getString("id")
    }

    private void addImagingOverallResult(String deviceId, String secret, String examId,
                                         String markerCode, String markerName, String category,
                                         double value, String valueText) {
        def path = "${BASE_PATH}/${examId}/results"
        def request = """
        {
            "results": [{
                "markerCode": "${markerCode}",
                "markerName": "${markerName}",
                "category": "${category}",
                "valueNumeric": ${value},
                "valueText": "${valueText}",
                "sortOrder": 1
            }]
        }
        """
        authenticatedPostRequestWithBody(deviceId, secret, path, request)
                .post(path)
                .then()
                .statusCode(201)
    }
}
