package com.healthassistant

import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Integration tests for AI Assistant body measurement mutation tools.
 * Tests tool methods directly via Spring ApplicationContext (Groovy duck typing)
 * to verify data is correctly persisted through facades and projections.
 *
 * These tests define the contract for three new tools:
 * - recordBodyMeasurement: creates a BodyMeasurementRecorded.v1 event
 * - updateBodyMeasurement: corrects an existing measurement via EventCorrected.v1
 * - deleteBodyMeasurement: deletes a measurement via EventDeleted.v1
 */
@Title("Feature: AI Assistant Body Measurement Mutation Tools")
class BodyMeasurementAiToolsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-body-meas-ai"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    ApplicationContext applicationContext

    private Object healthTools

    def setup() {
        healthTools = applicationContext.getBean("healthTools")
        cleanupEventsForDevice(DEVICE_ID)
        cleanupAllProjectionsForDevice(DEVICE_ID)
    }

    private ToolContext createToolContext() {
        return new ToolContext(Map.of("deviceId", DEVICE_ID))
    }

    private boolean isMutationSuccess(Object result) {
        return result.class.simpleName == "MutationSuccess"
    }

    private boolean isToolError(Object result) {
        return result.class.simpleName == "ToolError"
    }

    // ===================== recordBodyMeasurement =====================

    def "recordBodyMeasurement should create a body measurement and return MutationSuccess"() {
        when: "recording a body measurement through the tool"
        def result = healthTools.recordBodyMeasurement(
                null,       // measuredAt - defaults to now
                null,       // bicepsLeftCm
                null,       // bicepsRightCm
                null,       // forearmLeftCm
                null,       // forearmRightCm
                null,       // chestCm
                "82.0",     // waistCm
                null,       // abdomenCm
                null,       // hipsCm
                null,       // neckCm
                null,       // shouldersCm
                null,       // thighLeftCm
                null,       // thighRightCm
                null,       // calfLeftCm
                null,       // calfRightCm
                null,       // notes
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message().contains("Body measurement recorded")
        result.data() != null
        result.data().eventId() != null

        and: "measurement is persisted and visible via API"
        def response = waitForApiResponse("/v1/body-measurements/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.waistCm") == 82.0f
    }

    def "recordBodyMeasurement should create a measurement with multiple body parts"() {
        when: "recording a body measurement with waist and bicepsLeft"
        def result = healthTools.recordBodyMeasurement(
                null,       // measuredAt
                "38.5",     // bicepsLeftCm
                null,       // bicepsRightCm
                null,       // forearmLeftCm
                null,       // forearmRightCm
                null,       // chestCm
                "82.0",     // waistCm
                null,       // abdomenCm
                null,       // hipsCm
                null,       // neckCm
                null,       // shouldersCm
                null,       // thighLeftCm
                null,       // thighRightCm
                null,       // calfLeftCm
                null,       // calfRightCm
                null,       // notes
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)

        and: "both measurements are persisted"
        def response = waitForApiResponse("/v1/body-measurements/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.waistCm") == 82.0f
        response.get("measurement.bicepsLeftCm") == 38.5f
    }

    def "recordBodyMeasurement should accept custom measuredAt timestamp"() {
        given: "a specific timestamp"
        def yesterday = Instant.now().minus(1, ChronoUnit.DAYS).toString()

        when: "recording a body measurement with custom timestamp"
        def result = healthTools.recordBodyMeasurement(
                yesterday,  // measuredAt
                null,       // bicepsLeftCm
                null,       // bicepsRightCm
                null,       // forearmLeftCm
                null,       // forearmRightCm
                "102.0",    // chestCm
                null,       // waistCm
                null,       // abdomenCm
                null,       // hipsCm
                null,       // neckCm
                null,       // shouldersCm
                null,       // thighLeftCm
                null,       // thighRightCm
                null,       // calfLeftCm
                null,       // calfRightCm
                "Morning measurement",  // notes
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
    }

    def "recordBodyMeasurement should return ToolError when no measurements are provided"() {
        when: "recording a body measurement with all fields null"
        def result = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("at least one measurement")
    }

    def "recordBodyMeasurement should return ToolError for non-numeric measurement value"() {
        when: "recording a body measurement with non-numeric waist"
        def result = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "abc", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("waistCm")
    }

    def "recordBodyMeasurement should return ToolError for negative measurement value"() {
        when: "recording a body measurement with negative waist"
        def result = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "-5.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("positive")
    }

    def "recordBodyMeasurement should return ToolError for invalid measuredAt timestamp"() {
        when: "recording a body measurement with invalid timestamp"
        def result = healthTools.recordBodyMeasurement(
                "not-a-timestamp", null, null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("measuredAt")
    }

    def "recordBodyMeasurement should record all 14 body part measurements"() {
        when: "recording a body measurement with all body parts"
        def result = healthTools.recordBodyMeasurement(
                null,       // measuredAt
                "38.5",     // bicepsLeftCm
                "39.0",     // bicepsRightCm
                "30.0",     // forearmLeftCm
                "30.5",     // forearmRightCm
                "102.0",    // chestCm
                "82.0",     // waistCm
                "85.0",     // abdomenCm
                "98.0",     // hipsCm
                "40.0",     // neckCm
                "120.0",    // shouldersCm
                "58.0",     // thighLeftCm
                "58.5",     // thighRightCm
                "38.0",     // calfLeftCm
                "38.5",     // calfRightCm
                "Full body measurement", // notes
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)

        and: "all measurements are persisted"
        def measurementId = result.data().measurementId()
        def response = waitForApiResponse("/v1/body-measurements/${measurementId}", DEVICE_ID, SECRET_BASE64)
        response.get("bicepsLeftCm") == 38.5f
        response.get("bicepsRightCm") == 39.0f
        response.get("forearmLeftCm") == 30.0f
        response.get("forearmRightCm") == 30.5f
        response.get("chestCm") == 102.0f
        response.get("waistCm") == 82.0f
        response.get("abdomenCm") == 85.0f
        response.get("hipsCm") == 98.0f
        response.get("neckCm") == 40.0f
        response.get("shouldersCm") == 120.0f
        response.get("thighLeftCm") == 58.0f
        response.get("thighRightCm") == 58.5f
        response.get("calfLeftCm") == 38.0f
        response.get("calfRightCm") == 38.5f
        response.get("notes") == "Full body measurement"
    }

    // ===================== deleteBodyMeasurement =====================

    def "deleteBodyMeasurement should remove a body measurement"() {
        given: "a body measurement exists"
        def createResult = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )
        def eventId = createResult.data().eventId()

        when: "deleting the body measurement"
        def result = healthTools.deleteBodyMeasurement(eventId, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message() == "Body measurement deleted successfully"
    }

    def "deleteBodyMeasurement should return ToolError for empty eventId"() {
        when: "deleting with empty eventId"
        def result = healthTools.deleteBodyMeasurement("", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("eventId")
    }

    def "deleteBodyMeasurement should return ToolError for null eventId"() {
        when: "deleting with null eventId"
        def result = healthTools.deleteBodyMeasurement(null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("eventId")
    }

    def "deleteBodyMeasurement should return ToolError for overly long eventId"() {
        when: "deleting with eventId longer than 64 characters"
        def longEventId = "x" * 65
        def result = healthTools.deleteBodyMeasurement(longEventId, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("too long")
    }

    def "deleteBodyMeasurement should return ToolError for non-existent eventId"() {
        when: "deleting with a fake eventId"
        def result = healthTools.deleteBodyMeasurement("non-existent-event-id", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("not found")
    }

    // ===================== updateBodyMeasurement =====================

    def "updateBodyMeasurement should modify an existing body measurement"() {
        given: "a body measurement exists with waist 82cm"
        def createResult = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )
        def eventId = createResult.data().eventId()

        when: "updating the waist to 80cm"
        def result = healthTools.updateBodyMeasurement(
                eventId,
                null,       // measuredAt - keep original
                null,       // bicepsLeftCm
                null,       // bicepsRightCm
                null,       // forearmLeftCm
                null,       // forearmRightCm
                null,       // chestCm
                "80.0",     // waistCm - updated
                null,       // abdomenCm
                null,       // hipsCm
                null,       // neckCm
                null,       // shouldersCm
                null,       // thighLeftCm
                null,       // thighRightCm
                null,       // calfLeftCm
                null,       // calfRightCm
                null,       // notes
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message() == "Body measurement updated successfully"
    }

    def "updateBodyMeasurement should return ToolError for empty eventId"() {
        when: "updating with empty eventId"
        def result = healthTools.updateBodyMeasurement(
                "", null, null, null, null, null, null, "80.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("eventId")
    }

    def "updateBodyMeasurement should return ToolError when no measurements are provided"() {
        given: "a body measurement exists"
        def createResult = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )
        def eventId = createResult.data().eventId()

        when: "updating with all measurement fields null"
        def result = healthTools.updateBodyMeasurement(
                eventId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("at least one measurement")
    }

    def "updateBodyMeasurement should return ToolError for non-existent eventId"() {
        when: "updating with a fake eventId"
        def result = healthTools.updateBodyMeasurement(
                "non-existent-event-id", null, null, null, null, null, null, "80.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("not found")
    }

    def "updateBodyMeasurement should return ToolError for non-numeric measurement value"() {
        given: "a body measurement exists"
        def createResult = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )
        def eventId = createResult.data().eventId()

        when: "updating with non-numeric value"
        def result = healthTools.updateBodyMeasurement(
                eventId, null, null, null, null, null, null, "abc", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("waistCm")
    }

    // ===================== getBodyMeasurementsData should return eventId =====================

    def "getBodyMeasurementsData should return measurements with eventIds for mutation tools"() {
        given: "a body measurement exists"
        healthTools.recordBodyMeasurement(
                null, "38.5", null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        when: "getting body measurements data via the read tool"
        def today = LocalDate.now().toString()
        def result = healthTools.getBodyMeasurementsData(today, today, createToolContext())

        then: "result contains measurements with eventIds"
        !isToolError(result)
        result.measurementCount() >= 1
        result.measurements().every { it.eventId() != null && !it.eventId().isEmpty() }
    }

    // ===================== Full flow: record, query, update, delete =====================

    def "full body measurement flow: record, query eventId, update, verify update"() {
        when: "recording a body measurement"
        def createResult = healthTools.recordBodyMeasurement(
                null, "38.5", null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "measurement is created"
        isMutationSuccess(createResult)
        def eventId = createResult.data().eventId()
        eventId != null

        when: "querying measurements to get eventId"
        def today = LocalDate.now().toString()
        def rangeResult = healthTools.getBodyMeasurementsData(today, today, createToolContext())

        then: "measurement is visible with eventId"
        rangeResult.measurementCount() >= 1
        rangeResult.measurements().any { it.eventId() == eventId }

        when: "updating waist from 82 to 80"
        def updateResult = healthTools.updateBodyMeasurement(
                eventId, null, "38.5", null, null, null, null, "80.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "update is successful"
        isMutationSuccess(updateResult)

        and: "updated value is visible via API"
        def response = waitForApiResponse("/v1/body-measurements/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.waistCm") == 80.0f
        response.get("measurement.bicepsLeftCm") == 38.5f
    }

    def "full body measurement flow: record, query eventId, delete, verify deletion"() {
        when: "recording a body measurement"
        def createResult = healthTools.recordBodyMeasurement(
                null, null, null, null, null, null, "82.0", null, null, null, null, null, null, null, null, null,
                createToolContext()
        )

        then: "measurement is created"
        isMutationSuccess(createResult)
        def eventId = createResult.data().eventId()

        when: "deleting the measurement using eventId"
        def deleteResult = healthTools.deleteBodyMeasurement(eventId, createToolContext())

        then: "measurement is deleted"
        isMutationSuccess(deleteResult)
        deleteResult.message() == "Body measurement deleted successfully"

        and: "measurement is no longer visible via API"
        def latestResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/latest")
                .get("/v1/body-measurements/latest")
                .then()
                .extract()
        // Either 404 (no measurements) or latest doesn't contain the deleted measurement
        latestResponse.statusCode() == 404 || latestResponse.body().jsonPath().get("measurement.waistCm") != 82.0f
    }
}
