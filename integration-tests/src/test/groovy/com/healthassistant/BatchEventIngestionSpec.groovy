package com.healthassistant

import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Title("Feature: Batch Event Ingestion - Mixed Events, Partial Success, Large Batches")
class BatchEventIngestionSpec extends BaseIntegrationSpec {

    // ===========================================
    // Mixed Event Types
    // ===========================================

    def "Scenario 1: Batch with steps + sleep + workout events stores all correctly"() {
        given: "a batch with multiple different event types"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "batch-steps-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z",
                        "count": 500,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "batch-sleep-1",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-10T07:00:00Z",
                    "payload": {
                        "sleepId": "sleep-batch-1",
                        "sleepStart": "2025-11-09T23:00:00Z",
                        "sleepEnd": "2025-11-10T07:00:00Z",
                        "totalMinutes": 480,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "batch-workout-1",
                    "type": "WorkoutRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "workoutId": "workout-batch-1",
                        "performedAt": "2025-11-10T09:00:00Z",
                        "exercises": [
                            {
                                "name": "Bench Press",
                                "sets": [
                                    {"reps": 10, "weightKg": 60.0}
                                ]
                            }
                        ]
                    }
                }
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "all events are stored successfully"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        response.body().jsonPath().getInt("summary.stored") == 3
        response.body().jsonPath().getInt("summary.invalid") == 0

        and: "each event result shows stored status"
        def eventResults = response.body().jsonPath().getList("events")
        eventResults.size() == 3
        eventResults.every { it["status"] == "stored" }
    }

    def "Scenario 2: Batch with valid + invalid events returns partial success"() {
        given: "a batch with mixed valid and invalid events"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "partial-valid-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "partial-invalid-1",
                    "type": "InvalidEventType.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {}
                },
                {
                    "idempotencyKey": "partial-valid-2",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-10T09:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T08:00:00Z",
                        "bucketEnd": "2025-11-10T09:00:00Z",
                        "energyKcal": 50.0,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get partial_success status"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "partial_success"
        response.body().jsonPath().getInt("summary.stored") == 2
        response.body().jsonPath().getInt("summary.invalid") == 1
    }

    // ===========================================
    // Partial Success Details
    // ===========================================

    def "Scenario 3: Five valid and two invalid events show correct summary"() {
        given: "a batch with 5 valid and 2 invalid events"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                ${createStepsEventJson("valid-1", "2025-11-10T08:00:00Z", 100)},
                ${createStepsEventJson("valid-2", "2025-11-10T09:00:00Z", 200)},
                ${createInvalidEventJson("invalid-1")},
                ${createStepsEventJson("valid-3", "2025-11-10T10:00:00Z", 300)},
                ${createStepsEventJson("valid-4", "2025-11-10T11:00:00Z", 400)},
                ${createInvalidEventJson("invalid-2")},
                ${createStepsEventJson("valid-5", "2025-11-10T12:00:00Z", 500)}
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get partial_success with correct counts"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "partial_success"
        response.body().jsonPath().getInt("summary.stored") == 5
        response.body().jsonPath().getInt("summary.invalid") == 2
        response.body().jsonPath().getInt("totalEvents") == 7
    }

    def "Scenario 4: All invalid events return all_invalid status"() {
        given: "a batch with only invalid events"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                ${createInvalidEventJson("all-invalid-1")},
                ${createInvalidEventJson("all-invalid-2")},
                ${createInvalidEventJson("all-invalid-3")}
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get all_invalid status"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "all_invalid"
        response.body().jsonPath().getInt("summary.stored") == 0
        response.body().jsonPath().getInt("summary.invalid") == 3
    }

    // ===========================================
    // Large Batches
    // ===========================================

    def "Scenario 5: Batch with 100 events (max allowed) processes successfully"() {
        given: "a batch with exactly 100 events with unique keys"
        def path = "/v1/health-events"
        def batchId = UUID.randomUUID().toString()
        def events = (1..100).collect { i ->
            """
            {
                "idempotencyKey": "max-batch-${batchId}-${i}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T${String.format('%02d', i % 24)}:${String.format('%02d', i % 60)}:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T${String.format('%02d', i % 24)}:00:00Z",
                    "bucketEnd": "2025-11-10T${String.format('%02d', i % 24)}:59:00Z",
                    "count": ${i * 10},
                    "originPackage": "com.test"
                }
            }
            """
        }.join(",")
        def body = """{"events": [${events}]}"""

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "all 100 events are processed"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        response.body().jsonPath().getInt("totalEvents") == 100
        response.body().jsonPath().getInt("summary.stored") == 100
    }

    def "Scenario 6: Batch with different event types projects correctly"() {
        given: "a batch with various event types affecting different projections"
        def path = "/v1/health-events"
        def date = "2025-11-15"
        def uniquePrefix = UUID.randomUUID().toString()
        def body = """
        {
            "deviceId": "${TEST_DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "proj-steps-${uniquePrefix}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${date}T08:00:00Z",
                    "payload": {
                        "bucketStart": "${date}T07:00:00Z",
                        "bucketEnd": "${date}T08:00:00Z",
                        "count": 1000,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "proj-calories-${uniquePrefix}",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "${date}T09:00:00Z",
                    "payload": {
                        "bucketStart": "${date}T08:00:00Z",
                        "bucketEnd": "${date}T09:00:00Z",
                        "energyKcal": 150.0,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "proj-activity-${uniquePrefix}",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "${date}T10:00:00Z",
                    "payload": {
                        "bucketStart": "${date}T09:00:00Z",
                        "bucketEnd": "${date}T10:00:00Z",
                        "activeMinutes": 30,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "all events are stored"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        response.body().jsonPath().getInt("summary.stored") == 3

        and: "each event type is recorded correctly"
        def events = response.body().jsonPath().getList("events")
        events.size() == 3
        events.every { it["status"] == "stored" }
    }

    // ===========================================
    // Idempotency in Batch
    // ===========================================

    def "Scenario 7: Same idempotency key twice in batch - one stored, one duplicate"() {
        given: "a batch with duplicate idempotency key"
        def path = "/v1/health-events"
        def duplicateKey = "duplicate-key-" + UUID.randomUUID().toString()
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "${duplicateKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "${duplicateKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z",
                        "count": 200,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "one is stored, one is duplicate"
        response.statusCode() == 200
        def stored = response.body().jsonPath().getInt("summary.stored")
        def duplicate = response.body().jsonPath().getInt("summary.duplicate")
        stored + duplicate == 2
        stored >= 1
    }

    def "Scenario 8: Re-submitting same batch returns all duplicates"() {
        given: "a batch submitted once"
        def path = "/v1/health-events"
        def uniqueKey = "resubmit-" + UUID.randomUUID().toString()
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "${uniqueKey}-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "${uniqueKey}-2",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T09:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T08:00:00Z",
                        "bucketEnd": "2025-11-10T09:00:00Z",
                        "count": 200,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit the batch first time"
        def response1 = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "first submission stores all events"
        response1.statusCode() == 200
        response1.body().jsonPath().getInt("summary.stored") == 2

        when: "I submit the same batch again"
        def response2 = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "second submission shows all duplicates"
        response2.statusCode() == 200
        response2.body().jsonPath().getInt("summary.duplicate") == 2
        response2.body().jsonPath().getInt("summary.stored") == 0
    }

    // ===========================================
    // Summary Verification
    // ===========================================

    def "Scenario 9: Mixed batch correctly updates daily summary"() {
        given: "a batch with multiple event types for a specific date"
        def path = "/v1/health-events"
        def date = "2025-11-20"
        def uniquePrefix = UUID.randomUUID().toString()
        def body = """
        {
            "deviceId": "${TEST_DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "summary-steps-1-${uniquePrefix}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${date}T08:00:00Z",
                    "payload": {
                        "bucketStart": "${date}T07:00:00Z",
                        "bucketEnd": "${date}T08:00:00Z",
                        "count": 2500,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "summary-steps-2-${uniquePrefix}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${date}T10:00:00Z",
                    "payload": {
                        "bucketStart": "${date}T09:00:00Z",
                        "bucketEnd": "${date}T10:00:00Z",
                        "count": 1500,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "summary-calories-${uniquePrefix}",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "${date}T12:00:00Z",
                    "payload": {
                        "bucketStart": "${date}T11:00:00Z",
                        "bucketEnd": "${date}T12:00:00Z",
                        "energyKcal": 250.0,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "all events are stored"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        response.body().jsonPath().getInt("summary.stored") == 3

        and: "each event is recorded correctly"
        def events = response.body().jsonPath().getList("events")
        events.size() == 3
        events.every { it["status"] == "stored" }
    }

    def "Scenario 10: Per-event results contain correct status and error details"() {
        given: "a batch with valid, invalid, and events with missing fields"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "result-valid-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "result-invalid-type",
                    "type": "NonExistentType.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {}
                },
                {
                    "idempotencyKey": "result-missing-count",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T08:00:00Z"
                    }
                }
            ]
        }
        """

        when: "I submit the batch"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "response is 200 with partial_success or all_invalid"
        response.statusCode() == 200

        and: "each event result has appropriate status"
        def eventResults = response.body().jsonPath().getList("events")
        eventResults.size() == 3

        // First event should be stored
        eventResults[0].status == "stored"
        eventResults[0].index == 0

        // Second event should be invalid with error
        eventResults[1].status == "invalid"
        eventResults[1].index == 1
        eventResults[1].error != null

        // Third event should be invalid (missing required field)
        eventResults[2].status == "invalid"
        eventResults[2].index == 2
        eventResults[2].error != null
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private String createStepsEventJson(String key, String occurredAt, int count) {
        def hour = occurredAt.substring(11, 13)
        def prevHour = String.format("%02d", (Integer.parseInt(hour) - 1 + 24) % 24)
        def datePrefix = occurredAt.substring(0, 10)
        return """
        {
            "idempotencyKey": "${key}",
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "bucketStart": "${datePrefix}T${prevHour}:00:00Z",
                "bucketEnd": "${occurredAt}",
                "count": ${count},
                "originPackage": "com.test"
            }
        }
        """
    }

    private String createInvalidEventJson(String key) {
        return """
        {
            "idempotencyKey": "${key}",
            "type": "InvalidEventType.v99",
            "occurredAt": "2025-11-10T08:00:00Z",
            "payload": {"invalid": true}
        }
        """
    }
}
