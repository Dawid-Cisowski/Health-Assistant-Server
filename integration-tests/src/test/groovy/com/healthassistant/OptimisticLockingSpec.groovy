package com.healthassistant

import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests verifying optimistic locking behavior in projectors.
 * Tests that concurrent updates to the same projection are handled correctly
 * with @Version-based optimistic locking and retry mechanism.
 */
@Title("Feature: Optimistic Locking for Projections")
class OptimisticLockingSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-optlock"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        // Use dynamic date range based on test dates (test uses LocalDate.now().minusDays(10))
        def endDate = LocalDate.now(POLAND_ZONE)
        def startDate = endDate.minusDays(30)
        cleanupProjectionsForDateRange(DEVICE_ID, startDate, endDate)
    }

    def "Scenario 1: Batch of step events for same hour are all accumulated correctly"() {
        given: "a date and hour for testing"
        def date = LocalDate.now(POLAND_ZONE).minusDays(10)
        def dateStr = date.toString()
        // Use 14:00 Warsaw time = 13:00 UTC (winter)
        def hour = 14
        def utcHour = 13

        and: "a batch with 20 step events for the same hour"
        def events = (1..20).collect { i ->
            createStepsEventEntry(dateStr, utcHour, i, 50)
        }.join(",\n")

        def request = """
        {
            "events": [${events}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "all events are submitted in a single batch"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "all 20 events are processed and steps are accumulated"
        def response = waitForApiResponse("/v1/steps/daily/${dateStr}", DEVICE_ID, SECRET_BASE64, 10)
        response.getInt("totalSteps") == 1000 // 20 events * 50 steps each
        response.getList("hourlyBreakdown")[hour].steps == 1000
    }

    def "Scenario 2: Multiple rapid batches for same hour accumulate correctly"() {
        given: "a date for testing"
        def date = LocalDate.now(POLAND_ZONE).minusDays(11)
        def dateStr = date.toString()
        def hour = 15
        def utcHour = 14

        when: "3 batches of 10 events each are submitted rapidly"
        (0..2).each { batchNum ->
            def events = (1..10).collect { i ->
                createStepsEventEntry(dateStr, utcHour, batchNum * 10 + i, 100)
            }.join(",\n")

            def request = """
            {
                "events": [${events}],
                "deviceId": "${DEVICE_ID}"
            }
            """

            authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                    .post("/v1/health-events")
                    .then()
                    .statusCode(200)
        }

        then: "all 30 events are processed"
        def response = waitForApiResponse("/v1/steps/daily/${dateStr}", DEVICE_ID, SECRET_BASE64, 10)
        response.getInt("totalSteps") == 3000 // 30 events * 100 steps each
        response.getList("hourlyBreakdown")[hour].steps == 3000
    }

    def "Scenario 3: Same event resubmitted multiple times is idempotent"() {
        given: "a specific step event"
        def date = LocalDate.now(POLAND_ZONE).minusDays(12)
        def dateStr = date.toString()
        def utcHour = 10

        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|${dateStr}T${utcHour}:00:00Z-unique",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "${dateStr}T${utcHour}:00:01Z",
                "payload": {
                    "bucketStart": "${dateStr}T${utcHour}:00:00Z",
                    "bucketEnd": "${dateStr}T${utcHour}:00:01Z",
                    "count": 500,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "the same event is submitted 5 times"
        (1..5).each {
            authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                    .post("/v1/health-events")
                    .then()
                    .statusCode(200)
        }

        then: "only one projection exists with original step count"
        def response = waitForApiResponse("/v1/steps/daily/${dateStr}", DEVICE_ID, SECRET_BASE64, 10)
        response.getInt("totalSteps") == 500 // not 2500
    }

    private String createStepsEventEntry(String date, int utcHour, int index, int stepCount) {
        def minute = index % 60
        def bucketStart = "${date}T${String.format('%02d', utcHour)}:${String.format('%02d', minute)}:00Z"
        def bucketEnd = "${date}T${String.format('%02d', utcHour)}:${String.format('%02d', minute)}:01Z"
        """
            {
                "idempotencyKey": "${DEVICE_ID}|steps|${bucketStart}-${index}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "${bucketEnd}",
                "payload": {
                    "bucketStart": "${bucketStart}",
                    "bucketEnd": "${bucketEnd}",
                    "count": ${stepCount},
                    "originPackage": "com.google.android.apps.fitness"
                }
            }
        """
    }
}
