package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Medical Examinations Search (q + abnormal parameters)")
class MedicalExamsSearchSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-medical-search"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String BASE_PATH = "/v1/medical-exams"

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
    }

    // ===================== Text search (?q=) =====================

    def "should search examinations by title (case-insensitive)"() {
        given: "two examinations with different titles"
        createExam("MORPHOLOGY", "Morfologia kontrolna")
        createExam("ECG", "EKG Serca")

        when: "I search by partial title (lowercase)"
        def path = "${BASE_PATH}?q=morfologia"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the matching examination is returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
        exams[0].title == "Morfologia kontrolna"
    }

    def "should search examinations by title (case-insensitive, uppercase query)"() {
        given: "an examination with a mixed-case title"
        createExam("MORPHOLOGY", "Morfologia Pełna")

        when: "I search with uppercase query"
        def path = "${BASE_PATH}?q=MORFOLOGIA"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "the examination is found"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
    }

    def "should search examinations by marker name"() {
        given: "an examination with a specific marker"
        def examId = createExam("MORPHOLOGY", "Morfologia 1")
        addResult(examId, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)

        and: "another examination without that marker"
        createExam("ECG", "EKG kontrolne")

        when: "I search by marker name"
        def path = "${BASE_PATH}?q=hemoglobina"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the examination with that marker is returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
        exams[0].id == examId
    }

    def "should search examinations by marker code"() {
        given: "an examination with HGB marker"
        def examId = createExam("MORPHOLOGY", "Morfologia z HGB")
        addResult(examId, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)

        and: "another examination without HGB"
        createExam("LIPID_PANEL", "Lipidogram")

        when: "I search by marker code"
        def path = "${BASE_PATH}?q=hgb"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the examination with that marker code is returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
        exams[0].id == examId
    }

    def "should search examinations by marker category (section)"() {
        given: "an examination with a result in LIPID_PANEL category"
        def examId = createExam("LIPID_PANEL", "Lipidogram")
        addResult(examId, "CHOL", "Cholesterol", "LIPID_PANEL", 180.0, null, 200.0)

        and: "an examination with results in different category"
        def examId2 = createExam("MORPHOLOGY", "Morfologia")
        addResult(examId2, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)

        when: "I search by category name"
        def path = "${BASE_PATH}?q=lipid_panel"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the examination with LIPID_PANEL category is returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
        exams[0].id == examId
    }

    def "should not return duplicates when multiple markers match the search query"() {
        given: "one examination with two markers both matching the query"
        def examId = createExam("MORPHOLOGY", "Morfologia pełna")
        addResult(examId, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)
        addResult(examId, "HCT", "Hematokryt", "MORPHOLOGY", 42.0, 37.0, 47.0)

        when: "I search with a query matching both markers (both have 'hem' or category 'MORPHOLOGY')"
        def path = "${BASE_PATH}?q=morphology"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "examination appears only once (no duplicates)"
        def exams = response.body().jsonPath().getList("")
        exams.count { it.id == examId } == 1
    }

    def "should combine q with examType filter"() {
        given: "two morphology exams and one lipid panel exam, all with 'badanie' in title"
        def morphId1 = createExam("MORPHOLOGY", "Badanie morfologia 1")
        def morphId2 = createExam("MORPHOLOGY", "Badanie morfologia 2")
        createExam("LIPID_PANEL", "Badanie lipidogram")

        when: "I search with q=badanie and examType=MORPHOLOGY"
        def path = "${BASE_PATH}?q=badanie&examType=MORPHOLOGY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only morphology exams are returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 2
        exams.every { it.examTypeCode == "MORPHOLOGY" }
        exams.any { it.id == morphId1 }
        exams.any { it.id == morphId2 }
    }

    def "should combine q with date range filter"() {
        given: "examinations on different dates, with 'kontrolne' in title"
        def pastDate = LocalDate.now().minusDays(30).toString()
        def recentDate = LocalDate.now().toString()
        def oldExamId = createExamOnDate("MORPHOLOGY", "Badanie kontrolne stare", pastDate)
        def recentExamId = createExamOnDate("MORPHOLOGY", "Badanie kontrolne nowe", recentDate)

        when: "I search with q=kontrolne and restrict to last 7 days"
        def from = LocalDate.now().minusDays(7).toString()
        def to = LocalDate.now().toString()
        def path = "${BASE_PATH}?q=kontrolne&from=${from}&to=${to}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the recent examination is returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
        exams[0].id == recentExamId
    }

    def "should return empty list when no examinations match search query"() {
        given: "an existing examination"
        createExam("MORPHOLOGY", "Morfologia")

        when: "I search for a term that matches nothing"
        def path = "${BASE_PATH}?q=xyznonexistent123"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response is empty"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 0
    }

    def "should return all examinations when q is empty string"() {
        given: "two examinations"
        createExam("MORPHOLOGY", "Morfologia 1")
        createExam("ECG", "EKG 1")

        when: "I search with an empty q parameter"
        def path = "${BASE_PATH}?q="
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "all examinations are returned (no filtering applied)"
        def exams = response.body().jsonPath().getList("")
        exams.size() >= 2
    }

    def "search results are isolated by device"() {
        given: "an examination for our device matching the query"
        createExam("MORPHOLOGY", "Morfologia własna")

        when: "I search from a different device"
        def path = "${BASE_PATH}?q=morfologia"
        def response = authenticatedGetRequest("different-device-id", "ZGlmZmVyZW50LXNlY3JldC0xMjM=", path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "no examinations from our device are returned"
        def exams = response.body().jsonPath().getList("")
        exams.every { it.title != "Morfologia własna" }
    }

    // ===================== Abnormal filter (?abnormal=true) =====================

    def "should return examination with HIGH flag result when abnormal=true"() {
        given: "an examination with a HIGH marker"
        def abnormalExamId = createExam("MORPHOLOGY", "Badanie z odchyleniami")
        addResult(abnormalExamId, "WBC", "Leukocyty", "MORPHOLOGY", 15.0, 4.0, 10.0)

        and: "a normal examination"
        def normalExamId = createExam("MORPHOLOGY", "Badanie prawidłowe")
        addResult(normalExamId, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)

        when: "I filter with abnormal=true"
        def path = "${BASE_PATH}?abnormal=true"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the ABNORMAL examination is returned"
        def exams = response.body().jsonPath().getList("")
        exams.every { it.status == "ABNORMAL" }
        exams.any { it.id == abnormalExamId }

        and: "the normal examination is not in the results"
        exams.every { it.id != normalExamId }
    }

    def "should not return examinations with only NORMAL results when abnormal=true"() {
        given: "an examination where all markers are within normal ranges"
        def normalExamId = createExam("MORPHOLOGY", "Wszystko w normie")
        addResult(normalExamId, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)

        when: "I filter with abnormal=true"
        def path = "${BASE_PATH}?abnormal=true"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "the normal examination is not in the results"
        def exams = response.body().jsonPath().getList("")
        exams.every { it.id != normalExamId }
    }

    def "should combine abnormal=true with q filter"() {
        given: "an abnormal morphology exam with 'kontrolne' in title"
        def abnormalMorphId = createExam("MORPHOLOGY", "Badanie kontrolne morfologia")
        addResult(abnormalMorphId, "WBC", "Leukocyty", "MORPHOLOGY", 15.0, 4.0, 10.0)

        and: "a normal morphology exam with 'kontrolne' in title"
        def normalMorphId = createExam("MORPHOLOGY", "Badanie kontrolne 2")
        addResult(normalMorphId, "HGB", "Hemoglobina", "MORPHOLOGY", 15.5, 12.0, 18.0)

        and: "an abnormal lipid panel exam without 'kontrolne'"
        def abnormalLipidId = createExam("LIPID_PANEL", "Lipidogram nieprawidłowy")
        addResult(abnormalLipidId, "CHOL", "Cholesterol", "LIPID_PANEL", 250.0, null, 200.0)

        when: "I search with q=kontrolne AND abnormal=true"
        def path = "${BASE_PATH}?q=kontrolne&abnormal=true"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the abnormal exam with 'kontrolne' in title is returned"
        def exams = response.body().jsonPath().getList("")
        exams.size() == 1
        exams[0].id == abnormalMorphId
        exams[0].status == "ABNORMAL"
    }

    def "should return all examinations when abnormal=false (no filtering)"() {
        given: "an abnormal and a normal examination"
        def abnormalId = createExam("MORPHOLOGY", "Badanie nieprawidłowe")
        addResult(abnormalId, "WBC", "Leukocyty", "MORPHOLOGY", 15.0, 4.0, 10.0)

        def normalId = createExam("ECG", "EKG prawidłowe")

        when: "I request with abnormal=false"
        def path = "${BASE_PATH}?abnormal=false"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "both examinations are returned"
        def exams = response.body().jsonPath().getList("")
        exams.any { it.id == abnormalId }
        exams.any { it.id == normalId }
    }

    def "should return all examinations when abnormal param is absent"() {
        given: "an abnormal and a normal examination"
        def abnormalId = createExam("MORPHOLOGY", "Badanie nieprawidłowe 2")
        addResult(abnormalId, "WBC", "Leukocyty", "MORPHOLOGY", 15.0, 4.0, 10.0)

        def normalId = createExam("ECG", "EKG 2")

        when: "I request without abnormal parameter"
        def path = BASE_PATH
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "both examinations are returned"
        def exams = response.body().jsonPath().getList("")
        exams.any { it.id == abnormalId }
        exams.any { it.id == normalId }
    }

    // ===================== Helper methods =====================

    private String createExam(String examTypeCode, String title) {
        return createExamOnDate(examTypeCode, title, LocalDate.now().toString())
    }

    private String createExamOnDate(String examTypeCode, String title, String date) {
        def request = """
        {
            "examTypeCode": "${examTypeCode}",
            "title": "${title}",
            "date": "${date}"
        }
        """
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .statusCode(201)
                .extract()
        return response.body().jsonPath().getString("id")
    }

    private void addResult(String examId, String markerCode, String markerName, String category,
                           double value, Double refLow, Double refHigh) {
        def refLowJson = refLow != null ? refLow.toString() : "null"
        def refHighJson = refHigh != null ? refHigh.toString() : "null"
        def request = """
        {
            "results": [
                {
                    "markerCode": "${markerCode}",
                    "markerName": "${markerName}",
                    "category": "${category}",
                    "valueNumeric": ${value},
                    "unit": "units",
                    "refRangeLow": ${refLowJson},
                    "refRangeHigh": ${refHighJson},
                    "sortOrder": 1
                }
            ]
        }
        """
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results", request)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .statusCode(201)
    }
}
