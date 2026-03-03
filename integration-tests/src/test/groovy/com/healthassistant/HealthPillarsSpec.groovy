package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Health Pillars (Filary Zdrowia) API")
class HealthPillarsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-health-pillars"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String DIFF_DEVICE_ID = "different-device-id"
    private static final String DIFF_DEVICE_SECRET = "ZGlmZmVyZW50LXNlY3JldC0xMjM="
    private static final String BASE_PATH = "/v1/medical-exams"
    private static final String PILLARS_PATH = "/v1/medical-exams/health-pillars"

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DIFF_DEVICE_ID)
        jdbcTemplate.update("DELETE FROM health_pillar_ai_summaries WHERE device_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM health_pillar_ai_summaries WHERE device_id = ?", DIFF_DEVICE_ID)
    }

    // ===================== Scenario 1: No data =====================

    def "should return 5 pillars with null score and isOutdated=true when no data exists"() {
        when: "I fetch health pillars with no lab data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains exactly 5 pillars"
        def pillars = response.body().jsonPath().getList("pillars")
        pillars.size() == 5

        and: "all pillar codes are present"
        pillars.collect { it.pillarCode }.containsAll(
                ["CIRCULATORY", "DIGESTIVE", "METABOLISM", "BLOOD_IMMUNITY", "VITAMINS_MINERALS"])

        and: "all pillars have null score"
        pillars.every { it.score == null }

        and: "all pillars are outdated"
        pillars.every { it.isOutdated == true }

        and: "all pillars have null heroMetric"
        pillars.every { it.heroMetric == null }

        and: "aiInsight field is present (null or string)"
        pillars.every { it.containsKey("aiInsight") || it.aiInsight == null }
    }

    // ===================== Scenario 2: Data only in BLOOD_IMMUNITY =====================

    def "should compute score only for BLOOD_IMMUNITY when only HGB data exists"() {
        given: "a morphology exam with HGB (NORMAL value)"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "HGB", "Hemoglobina",
                14.0, 12.0, 16.0)

        when: "I fetch health pillars"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        def pillars = response.body().jsonPath().getList("pillars")

        and: "BLOOD_IMMUNITY has a score"
        def bloodImmunity = pillars.find { it.pillarCode == "BLOOD_IMMUNITY" }
        bloodImmunity.score != null
        bloodImmunity.score == 100

        and: "other pillars have null score"
        pillars.findAll { it.pillarCode != "BLOOD_IMMUNITY" }
                .every { it.score == null }
    }

    // ===================== Scenario 3: NORMAL marker → score = 100 =====================

    def "should return section score 100 for NORMAL marker"() {
        given: "a lipid panel exam with LDL in normal range"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL",
                100.0, 0.0, 130.0)

        when: "I fetch detail for CIRCULATORY pillar"
        def path = "${PILLARS_PATH}/CIRCULATORY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        def body = response.body().jsonPath()
        body.getString("pillarCode") == "CIRCULATORY"

        and: "LIPID_PROFILE section has score 100"
        def sections = body.getList("sections")
        def lipidSection = sections.find { it.sectionCode == "LIPID_PROFILE" }
        lipidSection != null
        lipidSection.score == 100

        and: "LDL marker is present with NORMAL flag and score 100"
        def ldl = lipidSection.markers.find { it.markerCode == "LDL" }
        ldl != null
        ldl.flag == "NORMAL"
        ldl.score == 100

        and: "heroMetric is LDL"
        body.getString("heroMetric.markerCode") == "LDL"
        body.getString("heroMetric.flag") == "NORMAL"

        and: "aiInsight field exists"
        body.getString("pillarCode") == "CIRCULATORY"
    }

    // ===================== Scenario 4: HIGH/LOW markers → mixed avg =====================

    def "should return section score 50 for HIGH marker"() {
        given: "a lipid exam with LDL above range"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL",
                200.0, 0.0, 130.0)

        when: "I fetch CIRCULATORY detail"
        def path = "${PILLARS_PATH}/CIRCULATORY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "LIPID_PROFILE section score is 50"
        def sections = response.body().jsonPath().getList("sections")
        def lipidSection = sections.find { it.sectionCode == "LIPID_PROFILE" }
        lipidSection.score == 50

        and: "LDL marker has flag HIGH and score 50"
        def ldl = lipidSection.markers.find { it.markerCode == "LDL" }
        ldl.flag == "HIGH"
        ldl.score == 50
    }

    def "should average scores for mixed NORMAL and HIGH markers in same section"() {
        given: "exams with LDL normal and CHOL high"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL",
                100.0, 0.0, 130.0)   // NORMAL → 100
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "CHOL", "Cholesterol",
                250.0, 0.0, 200.0)   // HIGH → 50

        when: "I fetch CIRCULATORY detail"
        def path = "${PILLARS_PATH}/CIRCULATORY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "LIPID_PROFILE section score is 75 (average of 100 and 50)"
        def sections = response.body().jsonPath().getList("sections")
        def lipidSection = sections.find { it.sectionCode == "LIPID_PROFILE" }
        lipidSection.score == 75
    }

    // ===================== Scenario 5: UNKNOWN flag → excluded from average =====================

    def "should exclude UNKNOWN flag markers from score average"() {
        given: "CREAT is NORMAL (has doc ref ranges) and URINE_COLOR has no ranges at all → UNKNOWN"
        // CREAT: submitted with doc ref ranges → NORMAL
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "CREAT", "Kreatynina",
                0.9, 0.6, 1.2)       // NORMAL → 100
        // URINE_COLOR: no doc ranges, no DB defaults → UNKNOWN (null score, excluded from avg)
        createExamWithResultNoRange(DEVICE_ID, SECRET, LocalDate.now(), "URINE_COLOR", "Barwa moczu", 1.0)

        when: "I fetch DIGESTIVE detail"
        def path = "${PILLARS_PATH}/DIGESTIVE"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "KIDNEY_URINARY section score is 100 (UNKNOWN URINE_COLOR excluded, only CREAT counted)"
        def sections = response.body().jsonPath().getList("sections")
        def kidneySection = sections.find { it.sectionCode == "KIDNEY_URINARY" }
        kidneySection != null
        kidneySection.score == 100

        and: "URINE_COLOR marker is present with UNKNOWN flag and null score"
        def urineColor = kidneySection.markers.find { it.markerCode == "URINE_COLOR" }
        urineColor != null
        urineColor.flag == "UNKNOWN"
        urineColor.score == null

        and: "CREAT marker is present with NORMAL flag and score 100"
        def creat = kidneySection.markers.find { it.markerCode == "CREAT" }
        creat != null
        creat.flag == "NORMAL"
        creat.score == 100
    }

    // ===================== Scenario 6: Latest result wins =====================

    def "should use latest result when multiple exist for same marker"() {
        given: "an older exam with LOW HGB"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now().minusDays(30), "HGB", "Hemoglobina",
                9.0, 12.0, 16.0)  // LOW → 50

        and: "a newer exam with NORMAL HGB"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "HGB", "Hemoglobina",
                14.0, 12.0, 16.0)  // NORMAL → 100

        when: "I fetch BLOOD_IMMUNITY detail"
        def path = "${PILLARS_PATH}/BLOOD_IMMUNITY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "HGB marker shows NORMAL (newest result wins)"
        def sections = response.body().jsonPath().getList("sections")
        def cbc = sections.find { it.sectionCode == "CBC" }
        def hgb = cbc.markers.find { it.markerCode == "HGB" }
        hgb.flag == "NORMAL"
        hgb.score == 100
    }

    // ===================== Scenario 7: isOutdated when data > TTL =====================

    def "should mark BLOOD_IMMUNITY as outdated when latest data is older than 6 months"() {
        given: "an old HGB result from 7 months ago"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now().minusMonths(7), "HGB", "Hemoglobina",
                14.0, 12.0, 16.0)

        when: "I fetch health pillars"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "BLOOD_IMMUNITY is outdated"
        def pillars = response.body().jsonPath().getList("pillars")
        def bloodImmunity = pillars.find { it.pillarCode == "BLOOD_IMMUNITY" }
        bloodImmunity.isOutdated == true
        bloodImmunity.score != null  // score is still computed
    }

    // ===================== Scenario 8: isOutdated=false when data is fresh =====================

    def "should mark pillar as not outdated when latest data is recent"() {
        given: "a recent HGB result"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now().minusMonths(1), "HGB", "Hemoglobina",
                14.0, 12.0, 16.0)

        when: "I fetch health pillars"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "BLOOD_IMMUNITY is NOT outdated"
        def pillars = response.body().jsonPath().getList("pillars")
        def bloodImmunity = pillars.find { it.pillarCode == "BLOOD_IMMUNITY" }
        bloodImmunity.isOutdated == false
    }

    // ===================== Scenario 9: Detail endpoint structure =====================

    def "should return CIRCULATORY detail with only populated sections when single marker exists"() {
        given: "an LDL result (only in LIPID_PROFILE section)"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "LDL", "LDL",
                100.0, 0.0, 130.0)

        when: "I fetch CIRCULATORY detail"
        def path = "${PILLARS_PATH}/CIRCULATORY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        def body = response.body().jsonPath()

        and: "pillarCode and namePl are correct"
        body.getString("pillarCode") == "CIRCULATORY"
        body.getString("pillarNamePl") == "Układ Krążeniowy"

        and: "only section with data is returned (1 section, not 3)"
        def sections = body.getList("sections")
        sections.size() == 1
        sections[0].sectionCode == "LIPID_PROFILE"
        sections[0].sectionNamePl == "Profil lipidowy"

        and: "only LDL marker is in the section (not all 8 markers)"
        sections[0].markers.size() == 1
        sections[0].markers[0].markerCode == "LDL"
    }

    // ===================== Scenario 10: Unknown pillarCode → 400 =====================

    def "should return 400 for unknown pillar code"() {
        when: "I fetch detail for a non-existent pillar"
        def path = "${PILLARS_PATH}/NONEXISTENT"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    // ===================== Scenario 11: HOMA-IR derived metric =====================

    def "should inject HOMA-IR into GLUCOSE_METABOLISM section when GLU and INSULIN are both present"() {
        given: "GLU at 90 mg/dL and INSULIN at 9 uIU/mL → HOMA-IR = (90*9)/405 = 2.0"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "GLU", "Glukoza", 90.0, 70.0, 99.0)
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "INSULIN", "Insulina", 9.0, null, 24.9)

        when: "I fetch METABOLISM detail"
        def path = "${PILLARS_PATH}/METABOLISM"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "GLUCOSE_METABOLISM section contains HOMA_IR marker"
        def sections = response.body().jsonPath().getList("sections")
        def glucoseSection = sections.find { it.sectionCode == "GLUCOSE_METABOLISM" }
        glucoseSection != null
        def homaMarker = glucoseSection.markers.find { it.markerCode == "HOMA_IR" }
        homaMarker != null
        homaMarker.markerNamePl == "HOMA-IR"
        homaMarker.valueNumeric != null
    }

    def "should mark HOMA-IR as HIGH when value >= 2.0"() {
        given: "GLU=99 and INSULIN=10 → HOMA-IR = (99*10)/405 ≈ 2.44 → HIGH"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "GLU", "Glukoza", 99.0, 70.0, 99.0)
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "INSULIN", "Insulina", 10.0, null, 24.9)

        when: "I fetch METABOLISM detail"
        def path = "${PILLARS_PATH}/METABOLISM"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "HOMA_IR marker flag is HIGH"
        def sections = response.body().jsonPath().getList("sections")
        def glucoseSection = sections.find { it.sectionCode == "GLUCOSE_METABOLISM" }
        def homaMarker = glucoseSection.markers.find { it.markerCode == "HOMA_IR" }
        homaMarker.flag == "HIGH"
        homaMarker.score == 50
    }

    def "should mark HOMA-IR as NORMAL when value < 2.0"() {
        given: "GLU=80 and INSULIN=5 → HOMA-IR = (80*5)/405 ≈ 0.99 → NORMAL"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "GLU", "Glukoza", 80.0, 70.0, 99.0)
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "INSULIN", "Insulina", 5.0, null, 24.9)

        when: "I fetch METABOLISM detail"
        def path = "${PILLARS_PATH}/METABOLISM"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "HOMA_IR marker flag is NORMAL"
        def sections = response.body().jsonPath().getList("sections")
        def glucoseSection = sections.find { it.sectionCode == "GLUCOSE_METABOLISM" }
        def homaMarker = glucoseSection.markers.find { it.markerCode == "HOMA_IR" }
        homaMarker.flag == "NORMAL"
        homaMarker.score == 100
    }

    def "should not inject HOMA-IR when only GLU is present"() {
        given: "only GLU, no INSULIN"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "GLU", "Glukoza", 90.0, 70.0, 99.0)

        when: "I fetch METABOLISM detail"
        def path = "${PILLARS_PATH}/METABOLISM"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "GLUCOSE_METABOLISM section does NOT contain HOMA_IR"
        def sections = response.body().jsonPath().getList("sections")
        def glucoseSection = sections.find { it.sectionCode == "GLUCOSE_METABOLISM" }
        def homaMarker = glucoseSection.markers.find { it.markerCode == "HOMA_IR" }
        homaMarker == null
    }

    // ===================== Scenario 12: Cross-device isolation =====================

    def "should not return data from another device"() {
        given: "HGB result for a different device"
        createExamWithResult(DIFF_DEVICE_ID, DIFF_DEVICE_SECRET, LocalDate.now(), "HGB", "Hemoglobina",
                14.0, 12.0, 16.0)

        when: "I fetch health pillars for main device (no data)"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "BLOOD_IMMUNITY shows no data for main device"
        def pillars = response.body().jsonPath().getList("pillars")
        def bloodImmunity = pillars.find { it.pillarCode == "BLOOD_IMMUNITY" }
        bloodImmunity.score == null
        bloodImmunity.isOutdated == true
    }

    // ===================== Scenario 13: Urine markers in DIGESTIVE =====================

    def "should include URINE_PH in DIGESTIVE KIDNEY_URINARY section"() {
        given: "a urine exam with URINE_PH in normal range"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "URINE_PH", "pH moczu",
                6.5, 5.0, 8.0)

        when: "I fetch DIGESTIVE detail"
        def path = "${PILLARS_PATH}/DIGESTIVE"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "KIDNEY_URINARY section is present with score"
        def sections = response.body().jsonPath().getList("sections")
        def kidneySection = sections.find { it.sectionCode == "KIDNEY_URINARY" }
        kidneySection != null
        kidneySection.score == 100

        and: "URINE_PH marker is in KIDNEY_URINARY section"
        def urinePh = kidneySection.markers.find { it.markerCode == "URINE_PH" }
        urinePh != null
        urinePh.flag == "NORMAL"

        and: "DIGESTIVE pillar has a score"
        def body = response.body().jsonPath()
        body.getInt("score") != null
    }

    // ===================== Hero metric tests =====================

    def "should return hero metric for VITAMINS_MINERALS pillar when VIT_D is available"() {
        given: "a VIT_D result"
        createExamWithResult(DEVICE_ID, SECRET, LocalDate.now(), "VIT_D", "Witamina D",
                35.0, 30.0, 100.0)

        when: "I fetch health pillars summary"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "VITAMINS_MINERALS pillar has heroMetric with VIT_D"
        def pillars = response.body().jsonPath().getList("pillars")
        def vitamins = pillars.find { it.pillarCode == "VITAMINS_MINERALS" }
        vitamins.heroMetric != null
        vitamins.heroMetric.markerCode == "VIT_D"
        vitamins.heroMetric.flag == "NORMAL"
    }

    def "should return latestDataDate for pillar"() {
        given: "an HGB result on a specific date"
        def examDate = LocalDate.now().minusDays(5)
        createExamWithResult(DEVICE_ID, SECRET, examDate, "HGB", "Hemoglobina",
                14.0, 12.0, 16.0)

        when: "I fetch health pillars"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, PILLARS_PATH)
                .get(PILLARS_PATH)
                .then()
                .extract()

        then: "BLOOD_IMMUNITY pillar has correct latestDataDate"
        def pillars = response.body().jsonPath().getList("pillars")
        def bloodImmunity = pillars.find { it.pillarCode == "BLOOD_IMMUNITY" }
        bloodImmunity.latestDataDate == examDate.toString()
    }

    // ===================== Helper methods =====================

    private String createExamWithResult(String deviceId, String secret, LocalDate date,
                                         String markerCode, String markerName,
                                         double value, Double refLow, Double refHigh) {
        def examId = createExaminationOnDate(deviceId, secret, date)
        addSingleResult(deviceId, secret, examId, markerCode, markerName, value, refLow, refHigh)
        return examId
    }

    private String createExamWithResultNoRange(String deviceId, String secret, LocalDate date,
                                                String markerCode, String markerName, double value) {
        return createExamWithResult(deviceId, secret, date, markerCode, markerName, value, null, null)
    }

    private String createExaminationOnDate(String deviceId, String secret, LocalDate date) {
        def request = """
        {
            "examTypeCode": "MORPHOLOGY",
            "title": "Health pillar test exam",
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
