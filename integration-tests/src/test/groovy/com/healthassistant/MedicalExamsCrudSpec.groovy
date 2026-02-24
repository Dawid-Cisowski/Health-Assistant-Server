package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Medical Examinations CRUD Operations")
class MedicalExamsCrudSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-medical-exams"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String BASE_PATH = "/v1/medical-exams"

    def setup() {
        // Cleanup examinations before each test for isolation
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
    }

    // ===================== GET /v1/medical-exams/types =====================

    def "should return seeded exam type definitions"() {
        when: "I fetch exam types"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/types")
                .get("${BASE_PATH}/types")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains seeded exam types"
        def types = response.body().jsonPath().getList("")
        types.size() > 0

        and: "MORPHOLOGY exam type is present with correct data"
        def morphology = types.find { it.code == "MORPHOLOGY" }
        morphology != null
        morphology.namePl == "Morfologia krwi"
        morphology.displayType == "LAB_RESULTS_TABLE"
        morphology.category == "LAB_TEST"
        morphology.specialties.contains("HEMATOLOGY")
    }

    def "should return available specialties"() {
        when: "I fetch specialties"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/specialties")
                .get("${BASE_PATH}/specialties")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains known specialties"
        def specialties = response.body().jsonPath().getList("")
        specialties.contains("HEMATOLOGY")
        specialties.contains("CARDIOLOGY")
        specialties.contains("ENDOCRINOLOGY")
    }

    // ===================== POST /v1/medical-exams =====================

    def "should create examination with valid data"() {
        given: "a valid examination request"
        def today = LocalDate.now().toString()
        def request = """
        {
            "examTypeCode": "MORPHOLOGY",
            "title": "Morfologia kontrolna",
            "date": "${today}",
            "laboratory": "Diagnostyka",
            "orderingDoctor": "Dr. Kowalski",
            "notes": "Rutynowe badanie kontrolne"
        }
        """

        when: "I create the examination"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .extract()

        then: "response status is 201 Created"
        response.statusCode() == 201

        and: "response contains correct examination data"
        def body = response.body().jsonPath()
        body.getString("id") != null
        body.getString("examTypeCode") == "MORPHOLOGY"
        body.getString("title") == "Morfologia kontrolna"
        body.getString("date") == today
        body.getString("status") == "COMPLETED"
        body.getString("displayType") == "LAB_RESULTS_TABLE"
        body.getString("laboratory") == "Diagnostyka"
        body.getString("orderingDoctor") == "Dr. Kowalski"
        body.getString("notes") == "Rutynowe badanie kontrolne"
        body.getString("source") == "MANUAL"
        body.getList("specialties").contains("HEMATOLOGY")
        body.getList("results").size() == 0
        body.getList("attachments").size() == 0
    }

    def "should fail to create examination with unknown exam type"() {
        given: "a request with non-existent exam type"
        def request = """
        {
            "examTypeCode": "NONEXISTENT_TYPE",
            "title": "Bad exam",
            "date": "${LocalDate.now()}"
        }
        """

        when: "I try to create the examination"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "should fail to create examination without required fields"() {
        given: "a request without title"
        def request = """
        {
            "examTypeCode": "MORPHOLOGY",
            "date": "${LocalDate.now()}"
        }
        """

        when: "I try to create the examination"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    // ===================== GET /v1/medical-exams/{examId} =====================

    def "should retrieve examination by ID"() {
        given: "an existing examination"
        def examId = createMorphologyExam("Badanie do pobrania")

        when: "I fetch the examination"
        def path = "${BASE_PATH}/${examId}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains correct data"
        def body = response.body().jsonPath()
        body.getString("id") == examId
        body.getString("title") == "Badanie do pobrania"
        body.getString("examTypeCode") == "MORPHOLOGY"
    }

    def "should return 404 for non-existent examination"() {
        given: "a random UUID"
        def fakeId = UUID.randomUUID().toString()

        when: "I try to fetch it"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${fakeId}")
                .get("${BASE_PATH}/${fakeId}")
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    def "should return 404 for examination belonging to different device"() {
        given: "an examination created by another device"
        def examId = createMorphologyExam("Other device exam")

        when: "I try to fetch it from a different device"
        def response = authenticatedGetRequest("different-device-id", "ZGlmZmVyZW50LXNlY3JldC0xMjM=", "${BASE_PATH}/${examId}")
                .get("${BASE_PATH}/${examId}")
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    // ===================== PUT /v1/medical-exams/{examId} =====================

    def "should update examination details"() {
        given: "an existing examination"
        def examId = createMorphologyExam("Oryginalna nazwa")

        and: "an update request"
        def updateRequest = """
        {
            "title": "Zaktualizowana nazwa",
            "laboratory": "MedLab",
            "notes": "Dodatkowe notatki"
        }
        """

        when: "I update the examination"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}", updateRequest)
                .put("${BASE_PATH}/${examId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains updated data"
        def body = response.body().jsonPath()
        body.getString("title") == "Zaktualizowana nazwa"
        body.getString("laboratory") == "MedLab"
        body.getString("notes") == "Dodatkowe notatki"
    }

    // ===================== DELETE /v1/medical-exams/{examId} =====================

    def "should delete examination"() {
        given: "an existing examination"
        def examId = createMorphologyExam("Badanie do usunięcia")

        when: "I delete the examination"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .delete("${BASE_PATH}/${examId}")
                .then()
                .extract()

        then: "response status is 204"
        response.statusCode() == 204

        and: "examination is no longer retrievable"
        authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .get("${BASE_PATH}/${examId}")
                .then()
                .statusCode(404)
    }

    def "should return 404 when deleting non-existent examination"() {
        given: "a random UUID"
        def fakeId = UUID.randomUUID().toString()

        when: "I try to delete it"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${fakeId}")
                .delete("${BASE_PATH}/${fakeId}")
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    // ===================== POST /v1/medical-exams/{examId}/results =====================

    def "should add lab results to examination with flag calculation"() {
        given: "an existing examination"
        def examId = createMorphologyExam("Morfologia z wynikami")

        and: "lab results with values within and outside reference ranges"
        def resultsRequest = """
        {
            "results": [
                {
                    "markerCode": "HGB",
                    "markerName": "Hemoglobina",
                    "category": "MORPHOLOGY",
                    "valueNumeric": 15.5,
                    "unit": "g/dl",
                    "refRangeLow": 12.0,
                    "refRangeHigh": 18.0,
                    "sortOrder": 1
                },
                {
                    "markerCode": "WBC",
                    "markerName": "Leukocyty",
                    "category": "MORPHOLOGY",
                    "valueNumeric": 11.5,
                    "unit": "tys/ul",
                    "refRangeLow": 4.0,
                    "refRangeHigh": 10.0,
                    "sortOrder": 2
                }
            ]
        }
        """

        when: "I add the results"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results", resultsRequest)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .extract()

        then: "response status is 201"
        response.statusCode() == 201

        and: "examination has 2 results"
        def body = response.body().jsonPath()
        body.getList("results").size() == 2

        and: "HGB is flagged as NORMAL (15.5 is within 12.0-18.0)"
        def hgb = body.getList("results").find { it.markerCode == "HGB" }
        hgb.flag == "NORMAL"
        hgb.valueNumeric == 15.5

        and: "WBC is flagged as HIGH (11.5 is above 10.0)"
        def wbc = body.getList("results").find { it.markerCode == "WBC" }
        wbc.flag == "HIGH"

        and: "examination status is ABNORMAL because WBC is HIGH"
        body.getString("status") == "ABNORMAL"
    }

    def "should populate default reference ranges from marker definitions"() {
        given: "an existing examination"
        def examId = createMorphologyExam("Morfologia z domyślnymi zakresami")

        and: "a result without lab-specific reference ranges"
        def resultsRequest = """
        {
            "results": [
                {
                    "markerCode": "HGB",
                    "markerName": "Hemoglobina",
                    "category": "MORPHOLOGY",
                    "valueNumeric": 15.5,
                    "unit": "g/dl",
                    "sortOrder": 1
                }
            ]
        }
        """

        when: "I add the result"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results", resultsRequest)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .extract()

        then: "response status is 201"
        response.statusCode() == 201

        and: "default reference ranges are populated from marker definition"
        def result = response.body().jsonPath().getList("results").find { it.markerCode == "HGB" }
        result.defaultRefRangeLow == 12.0
        result.defaultRefRangeHigh == 18.0

        and: "flag is calculated using default ranges"
        result.flag == "NORMAL"
    }

    def "should apply unit conversion for lipid panel markers"() {
        given: "a lipid panel examination"
        def examId = createExam("LIPID_PANEL", "Lipidogram")

        and: "a result in mmol/L (should convert to mg/dL)"
        def resultsRequest = """
        {
            "results": [
                {
                    "markerCode": "CHOL",
                    "markerName": "Cholesterol",
                    "category": "LIPID_PANEL",
                    "valueNumeric": 5.0,
                    "unit": "mmol/L",
                    "sortOrder": 1
                }
            ]
        }
        """

        when: "I add the result"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results", resultsRequest)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .extract()

        then: "response status is 201"
        response.statusCode() == 201

        and: "unit conversion was applied"
        def result = response.body().jsonPath().getList("results").find { it.markerCode == "CHOL" }
        result.conversionApplied == true
        result.originalUnit == "mmol/L"
        result.originalValueNumeric == 5.0
        result.unit == "mg/dL"

        and: "converted value is approximately 5.0 * 38.67 = 193.35"
        def convertedValue = result.valueNumeric as BigDecimal
        convertedValue >= 193.0 && convertedValue <= 194.0
    }

    // ===================== PUT /v1/medical-exams/{examId}/results/{resultId} =====================

    def "should update individual lab result"() {
        given: "an examination with a result"
        def examId = createMorphologyExam("Morfologia do aktualizacji wyniku")
        def resultId = addHgbResult(examId, 15.5)

        and: "an update request"
        def updateRequest = """
        {
            "valueNumeric": 16.0,
            "refRangeLow": 13.0,
            "refRangeHigh": 17.5
        }
        """

        when: "I update the result"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results/${resultId}", updateRequest)
                .put("${BASE_PATH}/${examId}/results/${resultId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "result values are updated"
        def body = response.body().jsonPath()
        body.getDouble("valueNumeric") == 16.0
        body.getDouble("refRangeLow") == 13.0
        body.getDouble("refRangeHigh") == 17.5
        body.getString("flag") == "NORMAL"
    }

    // ===================== DELETE /v1/medical-exams/{examId}/results/{resultId} =====================

    def "should delete individual lab result"() {
        given: "an examination with results"
        def examId = createMorphologyExam("Morfologia do usunięcia wyniku")
        def resultId = addHgbResult(examId, 15.5)

        when: "I delete the result"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results/${resultId}")
                .delete("${BASE_PATH}/${examId}/results/${resultId}")
                .then()
                .extract()

        then: "response status is 204"
        response.statusCode() == 204

        and: "examination has no results"
        def examResponse = authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .get("${BASE_PATH}/${examId}")
                .then()
                .extract()
        examResponse.body().jsonPath().getList("results").size() == 0
    }

    // ===================== GET /v1/medical-exams (list) =====================

    def "should list examinations for device"() {
        given: "two examinations"
        createMorphologyExam("Exam 1")
        createMorphologyExam("Exam 2")

        when: "I list examinations"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, BASE_PATH)
                .get(BASE_PATH)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains both examinations"
        def exams = response.body().jsonPath().getList("")
        exams.size() >= 2
    }

    def "should filter examinations by date range"() {
        given: "two examinations on different dates"
        def pastDate = LocalDate.now().minusDays(30).toString()
        createExamOnDate("MORPHOLOGY", "Old exam", pastDate)
        createExamOnDate("MORPHOLOGY", "Recent exam", LocalDate.now().toString())

        when: "I filter by recent dates only"
        def from = LocalDate.now().minusDays(7).toString()
        def to = LocalDate.now().toString()
        def path = "${BASE_PATH}?from=${from}&to=${to}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response contains only the recent exam"
        response.statusCode() == 200
        def exams = response.body().jsonPath().getList("")
        exams.every { it.title != "Old exam" }
        exams.any { it.title == "Recent exam" }
    }

    def "should filter examinations by exam type"() {
        given: "examinations of different types"
        createExam("MORPHOLOGY", "Blood test")
        createExam("ECG", "Heart check")

        when: "I filter by MORPHOLOGY"
        def path = "${BASE_PATH}?examType=MORPHOLOGY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response contains only morphology exams"
        response.statusCode() == 200
        def exams = response.body().jsonPath().getList("")
        exams.every { it.examTypeCode == "MORPHOLOGY" }
    }

    // ===================== GET /v1/medical-exams/markers/{markerCode}/trend =====================

    def "should return marker trend data"() {
        given: "two examinations with the same marker on different dates"
        def date1 = LocalDate.now().minusDays(60).toString()
        def date2 = LocalDate.now().minusDays(30).toString()
        def exam1 = createExamOnDate("MORPHOLOGY", "First test", date1)
        def exam2 = createExamOnDate("MORPHOLOGY", "Second test", date2)
        addHgbResult(exam1, 14.0)
        addHgbResult(exam2, 15.5)

        when: "I fetch the trend for HGB"
        def from = LocalDate.now().minusDays(90).toString()
        def to = LocalDate.now().toString()
        def path = "${BASE_PATH}/markers/HGB/trend?from=${from}&to=${to}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains trend data with data points sorted by date"
        def body = response.body().jsonPath()
        body.getString("markerCode") == "HGB"
        body.getString("markerName") != null
        def dataPoints = body.getList("dataPoints")
        dataPoints.size() >= 2
    }

    // ===================== GET /v1/medical-exams/history/{examTypeCode} =====================

    def "should return examination history for exam type"() {
        given: "multiple morphology examinations"
        createMorphologyExam("Morfologia 1")
        createMorphologyExam("Morfologia 2")
        createExam("ECG", "EKG control")

        when: "I fetch history for MORPHOLOGY"
        def path = "${BASE_PATH}/history/MORPHOLOGY"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains only morphology exams"
        def exams = response.body().jsonPath().getList("")
        exams.size() >= 2
        exams.every { it.examTypeCode == "MORPHOLOGY" }
    }

    // ===================== Full CRUD E2E Flow =====================

    def "should support full CRUD lifecycle: create, add results, update, then delete"() {
        when: "I create an examination"
        def createRequest = """
        {
            "examTypeCode": "LIPID_PANEL",
            "title": "Lipidogram kontrolny",
            "date": "${LocalDate.now()}",
            "laboratory": "Synevo"
        }
        """
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, BASE_PATH, createRequest)
                .post(BASE_PATH)
                .then()
                .extract()

        then: "examination is created"
        createResponse.statusCode() == 201
        def examId = createResponse.body().jsonPath().getString("id")

        when: "I add lab results"
        def resultsRequest = """
        {
            "results": [
                {
                    "markerCode": "CHOL",
                    "markerName": "Cholesterol",
                    "category": "LIPID_PANEL",
                    "valueNumeric": 220.0,
                    "unit": "mg/dL",
                    "refRangeLow": null,
                    "refRangeHigh": 200.0,
                    "sortOrder": 1
                },
                {
                    "markerCode": "HDL",
                    "markerName": "HDL",
                    "category": "LIPID_PANEL",
                    "valueNumeric": 55.0,
                    "unit": "mg/dL",
                    "refRangeLow": 40.0,
                    "refRangeHigh": null,
                    "sortOrder": 2
                }
            ]
        }
        """
        def addResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results", resultsRequest)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .extract()

        then: "results are added with correct flags"
        addResponse.statusCode() == 201
        def results = addResponse.body().jsonPath().getList("results")
        results.size() == 2

        def cholResult = results.find { it.markerCode == "CHOL" }
        cholResult.flag == "HIGH"

        def hdlResult = results.find { it.markerCode == "HDL" }
        hdlResult.flag == "NORMAL"

        and: "examination status is ABNORMAL"
        addResponse.body().jsonPath().getString("status") == "ABNORMAL"

        when: "I update the examination notes"
        def updateRequest = """
        {
            "notes": "Podwyższony cholesterol - zalecam dietę",
            "conclusions": "Hipercholesterolemia"
        }
        """
        def updateResponse = authenticatedPutRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}", updateRequest)
                .put("${BASE_PATH}/${examId}")
                .then()
                .extract()

        then: "examination is updated"
        updateResponse.statusCode() == 200
        updateResponse.body().jsonPath().getString("notes") == "Podwyższony cholesterol - zalecam dietę"
        updateResponse.body().jsonPath().getString("conclusions") == "Hipercholesterolemia"

        when: "I delete the examination"
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .delete("${BASE_PATH}/${examId}")
                .then()
                .extract()

        then: "examination is deleted"
        deleteResponse.statusCode() == 204

        and: "examination is no longer retrievable"
        authenticatedGetRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .get("${BASE_PATH}/${examId}")
                .then()
                .statusCode(404)
    }

    // ===================== Helper methods =====================

    private String createMorphologyExam(String title) {
        return createExam("MORPHOLOGY", title)
    }

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

    private String addHgbResult(String examId, double value) {
        def request = """
        {
            "results": [
                {
                    "markerCode": "HGB",
                    "markerName": "Hemoglobina",
                    "category": "MORPHOLOGY",
                    "valueNumeric": ${value},
                    "unit": "g/dl",
                    "sortOrder": 1
                }
            ]
        }
        """
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}/results", request)
                .post("${BASE_PATH}/${examId}/results")
                .then()
                .statusCode(201)
                .extract()
        def results = response.body().jsonPath().getList("results")
        return results.last().id
    }
}
