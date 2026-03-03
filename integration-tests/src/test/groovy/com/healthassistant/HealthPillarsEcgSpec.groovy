package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Health Pillars ECG Section (EKG z zegarka w filarze Układ Krążeniowy)")
class HealthPillarsEcgSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-hp-ecg"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String BASE_PATH = "/v1/medical-exams"
    private static final String CIRCULATORY_PATH = "/v1/medical-exams/health-pillars/CIRCULATORY"

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM health_pillar_ai_summaries WHERE device_id = ?", DEVICE_ID)
    }

    // ===================== Scenario 1: No ECG data → ECG_SMARTWATCH section absent =====================

    def "should not contain ECG_SMARTWATCH section in CIRCULATORY when no ECG data exists"() {
        when: "I fetch CIRCULATORY detail with no data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "CIRCULATORY does not contain ECG_SMARTWATCH section"
        def sections = response.body().jsonPath().getList("sections")
        sections.every { it.sectionCode != "ECG_SMARTWATCH" }
    }

    // ===================== Scenario 2: ECG_RHYTHM + ECG_AVG_HR → section appears with 2 markers =====================

    def "should show ECG_SMARTWATCH section in CIRCULATORY after adding ECG_RHYTHM and ECG_AVG_HR results"() {
        given: "ECG exam with ECG_RHYTHM (valueText) and ECG_AVG_HR (70 bpm)"
        def examId = createEcgExam(DEVICE_ID, SECRET, LocalDate.now())
        addEcgRhythmResult(DEVICE_ID, SECRET, examId, "Rytm zatokowy")
        addEcgAvgHrResult(DEVICE_ID, SECRET, examId, 70.0)

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "ECG_SMARTWATCH section is present with correct name"
        def sections = response.body().jsonPath().getList("sections")
        def ecgSection = sections.find { it.sectionCode == "ECG_SMARTWATCH" }
        ecgSection != null
        ecgSection.sectionNamePl == "EKG z zegarka"

        and: "ECG_SMARTWATCH section contains exactly 2 markers"
        ecgSection.markers.size() == 2
    }

    // ===================== Scenario 3: ECG_RHYTHM has correct valueText and UNKNOWN flag =====================

    def "should return ECG_RHYTHM with correct valueText, UNKNOWN flag and null score"() {
        given: "ECG exam with ECG_RHYTHM (categorical — valueNumeric=null)"
        def examId = createEcgExam(DEVICE_ID, SECRET, LocalDate.now())
        addEcgRhythmResult(DEVICE_ID, SECRET, examId, "Rytm zatokowy")

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "ECG_RHYTHM marker has correct valueText, UNKNOWN flag and null score"
        def sections = response.body().jsonPath().getList("sections")
        def ecgSection = sections.find { it.sectionCode == "ECG_SMARTWATCH" }
        ecgSection != null
        def rhythmMarker = ecgSection.markers.find { it.markerCode == "ECG_RHYTHM" }
        rhythmMarker != null
        rhythmMarker.valueText == "Rytm zatokowy"
        rhythmMarker.flag == "UNKNOWN"
        rhythmMarker.score == null
        rhythmMarker.valueNumeric == null
    }

    // ===================== Scenario 4: ECG_AVG_HR 70 bpm → NORMAL, score=100 =====================

    def "should return ECG_AVG_HR with NORMAL flag and score 100 for 70 bpm (within 60-100 default range)"() {
        given: "ECG exam with ECG_AVG_HR at 70 bpm — within default range 60-100"
        def examId = createEcgExam(DEVICE_ID, SECRET, LocalDate.now())
        addEcgAvgHrResult(DEVICE_ID, SECRET, examId, 70.0)

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "ECG_AVG_HR marker has NORMAL flag and score 100"
        def sections = response.body().jsonPath().getList("sections")
        def ecgSection = sections.find { it.sectionCode == "ECG_SMARTWATCH" }
        ecgSection != null
        def hrMarker = ecgSection.markers.find { it.markerCode == "ECG_AVG_HR" }
        hrMarker != null
        hrMarker.flag == "NORMAL"
        hrMarker.score == 100
        (hrMarker.valueNumeric as BigDecimal) == 70.0

        and: "reference ranges come from marker_definitions defaults (60-100)"
        (hrMarker.refRangeLow as BigDecimal) == 60.0
        (hrMarker.refRangeHigh as BigDecimal) == 100.0
    }

    // ===================== Scenario 5: ECG_AVG_HR HIGH (above range) =====================

    def "should return ECG_AVG_HR with HIGH flag and score 50 for 120 bpm (above default range 60-100)"() {
        given: "ECG exam with ECG_AVG_HR at 120 bpm — above default range"
        def examId = createEcgExam(DEVICE_ID, SECRET, LocalDate.now())
        addEcgAvgHrResult(DEVICE_ID, SECRET, examId, 120.0)

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "ECG_AVG_HR marker has HIGH flag and score 50"
        def sections = response.body().jsonPath().getList("sections")
        def ecgSection = sections.find { it.sectionCode == "ECG_SMARTWATCH" }
        ecgSection != null
        def hrMarker = ecgSection.markers.find { it.markerCode == "ECG_AVG_HR" }
        hrMarker != null
        hrMarker.flag == "HIGH"
        hrMarker.score == 50
    }

    // ===================== Scenario 6: ECG_AVG_HR LOW (below range) =====================

    def "should return ECG_AVG_HR with LOW flag and score 50 for 45 bpm (below default range 60-100)"() {
        given: "ECG exam with ECG_AVG_HR at 45 bpm — below default range"
        def examId = createEcgExam(DEVICE_ID, SECRET, LocalDate.now())
        addEcgAvgHrResult(DEVICE_ID, SECRET, examId, 45.0)

        when: "I fetch CIRCULATORY detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, CIRCULATORY_PATH)
                .get(CIRCULATORY_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "ECG_AVG_HR marker has LOW flag and score 50"
        def sections = response.body().jsonPath().getList("sections")
        def ecgSection = sections.find { it.sectionCode == "ECG_SMARTWATCH" }
        ecgSection != null
        def hrMarker = ecgSection.markers.find { it.markerCode == "ECG_AVG_HR" }
        hrMarker != null
        hrMarker.flag == "LOW"
        hrMarker.score == 50
    }

    // ===================== Helper methods =====================

    private String createEcgExam(String deviceId, String secret, LocalDate date) {
        def request = """{"examTypeCode": "ECG", "title": "EKG z zegarka", "date": "${date}"}"""
        def response = authenticatedPostRequestWithBody(deviceId, secret, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .statusCode(201)
                .extract()
        return response.body().jsonPath().getString("id")
    }

    private void addEcgRhythmResult(String deviceId, String secret, String examId, String rhythmText) {
        def path = "${BASE_PATH}/${examId}/results"
        def request = """
        {
            "results": [{
                "markerCode": "ECG_RHYTHM",
                "markerName": "Rytm EKG",
                "category": "ECG",
                "valueText": "${rhythmText}",
                "sortOrder": 1
            }]
        }
        """
        authenticatedPostRequestWithBody(deviceId, secret, path, request)
                .post(path)
                .then()
                .statusCode(201)
    }

    private void addEcgAvgHrResult(String deviceId, String secret, String examId, double bpm) {
        def path = "${BASE_PATH}/${examId}/results"
        def request = """
        {
            "results": [{
                "markerCode": "ECG_AVG_HR",
                "markerName": "Średnie tętno (EKG)",
                "category": "ECG",
                "valueNumeric": ${bpm},
                "unit": "bpm",
                "sortOrder": 2
            }]
        }
        """
        authenticatedPostRequestWithBody(deviceId, secret, path, request)
                .post(path)
                .then()
                .statusCode(201)
    }
}
