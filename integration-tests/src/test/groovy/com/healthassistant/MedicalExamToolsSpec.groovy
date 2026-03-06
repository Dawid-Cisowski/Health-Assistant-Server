package com.healthassistant

import com.healthassistant.medicalexams.api.dto.AddLabResultsRequest
import com.healthassistant.medicalexams.api.dto.CreateExaminationRequest
import com.healthassistant.medicalexams.api.dto.LabResultEntry
import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Title

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Integration tests for AI Assistant medical exam read tools.
 * Tests tool methods directly via Spring ApplicationContext
 * to verify they delegate correctly to MedicalExamsFacade.
 */
@Title("Feature: AI Assistant Medical Exam Tools")
class MedicalExamToolsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-assistant-medical"

    @Autowired
    ApplicationContext applicationContext

    private Object healthTools

    def setup() {
        healthTools = applicationContext.getBean("healthTools")
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
    }

    private ToolContext createToolContext() {
        return new ToolContext(Map.of("deviceId", DEVICE_ID))
    }

    private boolean isToolError(Object result) {
        return result.class.simpleName == "ToolError"
    }

    // ===================== getMedicalExams =====================

    def "getMedicalExams with no filters should return list of examinations"() {
        given: "an examination exists"
        medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "MORPHOLOGY", "Morfologia krwi", LocalDate.now().minusDays(10),
                null, null, "Lab Centrum", null, null, null, null, null, "MANUAL"
        ))

        when: "calling getMedicalExams with no filters"
        def result = healthTools.getMedicalExams(null, null, null, null, null, null, createToolContext())

        then: "result is a non-empty list"
        !isToolError(result)
        result instanceof List
        result.size() >= 1
    }

    def "getMedicalExams with date range filter should return matching exams"() {
        given: "an examination exists"
        medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "MORPHOLOGY", "Test exam", LocalDate.now().minusDays(1),
                null, null, null, null, null, null, null, null, "MANUAL"
        ))

        when: "calling getMedicalExams with valid date range"
        def startDate = LocalDate.now().minusDays(7).toString()
        def endDate = LocalDate.now().toString()
        def result = healthTools.getMedicalExams(null, null, startDate, endDate, null, null, createToolContext())

        then: "result is a list with at least one exam"
        !isToolError(result)
        result instanceof List
        result.size() >= 1
    }

    def "getMedicalExams with abnormalOnly filter should return list"() {
        when: "calling getMedicalExams with abnormalOnly=true"
        def result = healthTools.getMedicalExams(null, null, null, null, null, "true", createToolContext())

        then: "result is a list (may be empty)"
        !isToolError(result)
        result instanceof List
    }

    def "getMedicalExams with invalid startDate should return ToolError"() {
        when: "calling getMedicalExams with invalid date"
        def result = healthTools.getMedicalExams(null, null, "not-a-date", null, null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("Invalid date")
    }

    def "getMedicalExams with invalid endDate should return ToolError"() {
        when: "calling getMedicalExams with invalid end date"
        def result = healthTools.getMedicalExams(null, null, null, "bad-date", null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("Invalid date")
    }

    // ===================== getMedicalExamDetail =====================

    def "getMedicalExamDetail with valid UUID should return exam detail with lab results"() {
        given: "an examination with lab results exists"
        def exam = medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "MORPHOLOGY", "Morfologia krwi", LocalDate.now().minusDays(5),
                null, null, "Lab Centrum", null, null, null, null, null, "MANUAL"
        ))
        medicalExamsFacade.addResults(DEVICE_ID, exam.id(), new AddLabResultsRequest(List.of(
                new LabResultEntry("WBC", "Leukocyty", "HEMATOLOGY",
                        new BigDecimal("6.5"), "10^3/µL",
                        new BigDecimal("4.0"), new BigDecimal("10.0"),
                        null, null, 1)
        )))

        when: "calling getMedicalExamDetail with valid UUID"
        def result = healthTools.getMedicalExamDetail(exam.id().toString(), createToolContext())

        then: "result is the exam detail with lab results"
        !isToolError(result)
        result.id() == exam.id()
        result.title() == "Morfologia krwi"
        result.results().size() == 1
        result.results()[0].markerCode() == "WBC"
    }

    def "getMedicalExamDetail with invalid UUID should return ToolError"() {
        when: "calling getMedicalExamDetail with non-UUID string"
        def result = healthTools.getMedicalExamDetail("not-a-uuid", createToolContext())

        then: "result is a ToolError mentioning examId"
        isToolError(result)
        result.message().contains("examId")
    }

    def "getMedicalExamDetail with blank examId should return ToolError"() {
        when: "calling getMedicalExamDetail with blank examId"
        def result = healthTools.getMedicalExamDetail("", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    def "getMedicalExamDetail with non-existent UUID should return ToolError"() {
        when: "calling getMedicalExamDetail with a UUID that does not exist"
        def result = healthTools.getMedicalExamDetail(UUID.randomUUID().toString(), createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    // ===================== getMedicalExamHistory =====================

    def "getMedicalExamHistory should return history for an exam type"() {
        given: "two MORPHOLOGY examinations exist"
        medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "MORPHOLOGY", "Morfologia 1", LocalDate.now().minusDays(30),
                null, null, null, null, null, null, null, null, "MANUAL"
        ))
        medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "MORPHOLOGY", "Morfologia 2", LocalDate.now().minusDays(10),
                null, null, null, null, null, null, null, null, "MANUAL"
        ))

        when: "calling getMedicalExamHistory for MORPHOLOGY"
        def startDate = LocalDate.now().minusDays(60).toString()
        def endDate = LocalDate.now().toString()
        def result = healthTools.getMedicalExamHistory("MORPHOLOGY", startDate, endDate, createToolContext())

        then: "result is a list with at least 2 exams"
        !isToolError(result)
        result instanceof List
        result.size() >= 2
    }

    def "getMedicalExamHistory with no dates should return all exams of that type"() {
        given: "a LIPID_PANEL examination exists"
        medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "LIPID_PANEL", "Lipidogram", LocalDate.now().minusDays(5),
                null, null, null, null, null, null, null, null, "MANUAL"
        ))

        when: "calling getMedicalExamHistory with no dates"
        def result = healthTools.getMedicalExamHistory("LIPID_PANEL", null, null, createToolContext())

        then: "result is a list"
        !isToolError(result)
        result instanceof List
        result.size() >= 1
    }

    def "getMedicalExamHistory with blank examTypeCode should return ToolError"() {
        when: "calling getMedicalExamHistory with blank examTypeCode"
        def result = healthTools.getMedicalExamHistory("", null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("examTypeCode")
    }

    def "getMedicalExamHistory with invalid startDate should return ToolError"() {
        when: "calling getMedicalExamHistory with invalid start date"
        def result = healthTools.getMedicalExamHistory("MORPHOLOGY", "bad-date", null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("Invalid date")
    }

    // ===================== getMarkerTrend =====================

    def "getMarkerTrend should return trend data for a known marker with data"() {
        given: "an examination with a WBC lab result exists"
        def exam = medicalExamsFacade.createExamination(DEVICE_ID, new CreateExaminationRequest(
                "MORPHOLOGY", "Morfologia", LocalDate.now().minusDays(15),
                null, null, null, null, null, null, null, null, "MANUAL"
        ))
        medicalExamsFacade.addResults(DEVICE_ID, exam.id(), new AddLabResultsRequest(List.of(
                new LabResultEntry("WBC", "Leukocyty", "HEMATOLOGY",
                        new BigDecimal("6.5"), "10^3/µL",
                        new BigDecimal("4.0"), new BigDecimal("10.0"),
                        null, null, 1)
        )))

        when: "calling getMarkerTrend for WBC"
        def startDate = LocalDate.now().minusDays(30).toString()
        def endDate = LocalDate.now().toString()
        def result = healthTools.getMarkerTrend("WBC", startDate, endDate, createToolContext())

        then: "result contains trend data with at least one data point"
        !isToolError(result)
        result.markerCode() == "WBC"
        result.dataPoints().size() >= 1
    }

    def "getMarkerTrend with blank markerCode should return ToolError"() {
        when: "calling getMarkerTrend with blank marker code"
        def result = healthTools.getMarkerTrend("", null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("markerCode")
    }

    def "getMarkerTrend with invalid startDate should return ToolError"() {
        when: "calling getMarkerTrend with invalid date"
        def result = healthTools.getMarkerTrend("WBC", "not-a-date", null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("Invalid date")
    }

    // ===================== getHealthPillars =====================

    def "getHealthPillars should return dashboard with pillar list"() {
        when: "calling getHealthPillars"
        def result = healthTools.getHealthPillars(createToolContext())

        then: "result is a health pillars dashboard with pillars"
        !isToolError(result)
        result.pillars() != null
        result.pillars().size() > 0
    }

    // ===================== getHealthPillarDetail =====================

    def "getHealthPillarDetail should return detail for a valid pillar code"() {
        when: "calling getHealthPillarDetail for CIRCULATORY"
        def result = healthTools.getHealthPillarDetail("CIRCULATORY", createToolContext())

        then: "result is pillar detail response"
        !isToolError(result)
        result.pillarCode() == "CIRCULATORY"
    }

    def "getHealthPillarDetail with blank pillarCode should return ToolError"() {
        when: "calling getHealthPillarDetail with blank pillar code"
        def result = healthTools.getHealthPillarDetail("", createToolContext())

        then: "result is a ToolError mentioning pillarCode"
        isToolError(result)
        result.message().contains("pillarCode")
    }

    def "getHealthPillarDetail with unknown pillarCode should return ToolError"() {
        when: "calling getHealthPillarDetail with an unknown pillar code"
        def result = healthTools.getHealthPillarDetail("UNKNOWN_PILLAR_XYZ", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
    }

    // ===================== getAvailableExamTypes =====================

    def "getAvailableExamTypes should return non-empty list of exam type definitions"() {
        when: "calling getAvailableExamTypes"
        def result = healthTools.getAvailableExamTypes(createToolContext())

        then: "result is a non-empty list with MORPHOLOGY type"
        !isToolError(result)
        result instanceof List
        result.size() > 0
        result.any { it.code() == "MORPHOLOGY" }
    }
}
