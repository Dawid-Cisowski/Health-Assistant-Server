package com.healthassistant

import spock.lang.Title

/**
 * Integration tests for Event Validation (Feature 2)
 */
@Title("Feature 2: Event Validation")
class EventValidationSpec extends BaseIntegrationSpec {

    // ========== StepsBucketedRecorded.v1 Tests ==========

    def "Scenario 2.1: Steps event with count = 0"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "StepsBucketedRecorded event with count = 0"
        def body = """
        {
            "events": [{
                "idempotencyKey": "zero|steps|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 0,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with count = 0"
        def events = eventRepository.findAll()
        events.size() == 1
        events[0].payload.count == 0
    }

    def "Scenario 2.2: Steps event with large count (edge case)"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "StepsBucketedRecorded event with count = 999999"
        def body = """
        {
            "events": [{
                "idempotencyKey": "large|steps|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 999999,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with large count"
        eventRepository.findAll()[0].payload.count == 999999
    }

    def "Scenario 2.3: Missing bucketStart field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "StepsBucketedRecorded event without bucketStart field"
        def body = """
        {
            "events": [{
                "idempotencyKey": "missing|bucketStart|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: bucketStart")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.4: Negative count value"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "StepsBucketedRecorded event with count = -10"
        def body = """
        {
            "events": [{
                "idempotencyKey": "negative|count|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": -10,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("must be non-negative")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.5: Missing originPackage field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "StepsBucketedRecorded event without originPackage"
        def body = """
        {
            "events": [{
                "idempotencyKey": "missing|origin|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: originPackage")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    // ========== HeartRateSummaryRecorded.v1 Tests ==========

    def "Scenario 2.6: Missing avg field in HeartRate event"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "HeartRateSummaryRecorded event without avg field"
        def body = """
        {
            "events": [{
                "idempotencyKey": "missing|avg|${System.currentTimeMillis()}",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-10T10:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T10:00:00Z",
                    "bucketEnd": "2025-11-10T10:15:00Z",
                    "min": 60,
                    "max": 100,
                    "samples": 30,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: avg")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.7: Negative heart rate value"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "HeartRateSummaryRecorded event with avg = -10"
        def body = """
        {
            "events": [{
                "idempotencyKey": "negative|hr|${System.currentTimeMillis()}",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-10T10:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T10:00:00Z",
                    "bucketEnd": "2025-11-10T10:15:00Z",
                    "avg": -10,
                    "min": -5,
                    "max": 100,
                    "samples": 30,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("must be non-negative")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.8: samples less than 1"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "HeartRateSummaryRecorded event with samples = 0"
        def body = """
        {
            "events": [{
                "idempotencyKey": "zero|samples|${System.currentTimeMillis()}",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-10T10:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T10:00:00Z",
                    "bucketEnd": "2025-11-10T10:15:00Z",
                    "avg": 75,
                    "min": 60,
                    "max": 100,
                    "samples": 0,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("must be positive")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    // ========== SleepSessionRecorded.v1 Tests ==========

    def "Scenario 2.9: Valid SleepSessionRecorded event with stages"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "SleepSessionRecorded event with stages"
        def body = """
        {
            "events": [{
                "idempotencyKey": "sleep|stages|${System.currentTimeMillis()}",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-11-10T06:00:00Z",
                "payload": {
                    "sleepStart": "2025-11-09T22:00:00Z",
                    "sleepEnd": "2025-11-10T06:00:00Z",
                    "lightMinutes": 240,
                    "deepMinutes": 120,
                    "remMinutes": 90,
                    "awakeMinutes": 30,
                    "totalMinutes": 480,
                    "hasStages": true,
                    "originPackage": "com.example.sleep"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with all sleep data"
        def events = eventRepository.findAll()
        events.size() == 1
        events[0].eventType == "SleepSessionRecorded.v1"
        events[0].payload.totalMinutes == 480
    }

    def "Scenario 2.10: Valid SleepSessionRecorded event without stages"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "SleepSessionRecorded event without stages"
        def body = """
        {
            "events": [{
                "idempotencyKey": "sleep|nostages|${System.currentTimeMillis()}",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-11-10T06:00:00Z",
                "payload": {
                    "sleepStart": "2025-11-09T22:00:00Z",
                    "sleepEnd": "2025-11-10T06:00:00Z",
                    "totalMinutes": 480,
                    "hasStages": false,
                    "originPackage": "com.example.sleep"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved without stage details"
        eventRepository.findAll()[0].payload.hasStages == false
    }

    def "Scenario 2.11: Missing totalMinutes field in SleepSession"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "SleepSessionRecorded event without totalMinutes"
        def body = """
        {
            "events": [{
                "idempotencyKey": "sleep|missing|${System.currentTimeMillis()}",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-11-10T06:00:00Z",
                "payload": {
                    "sleepStart": "2025-11-09T22:00:00Z",
                    "sleepEnd": "2025-11-10T06:00:00Z",
                    "originPackage": "com.example.sleep"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: totalMinutes")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    // ========== ActiveCaloriesBurnedRecorded.v1 Tests ==========

    def "Scenario 2.12: Valid ActiveCaloriesBurnedRecorded event"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ActiveCaloriesBurnedRecorded event with energyKcal = 150.5"
        def body = """
        {
            "events": [{
                "idempotencyKey": "calories|test|${System.currentTimeMillis()}",
                "type": "ActiveCaloriesBurnedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "energyKcal": 150.5,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with energyKcal"
        eventRepository.findAll()[0].payload.energyKcal == 150.5
    }

    // ========== ActiveMinutesRecorded.v1 Tests ==========

    def "Scenario 2.13: Valid ActiveMinutesRecorded event"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ActiveMinutesRecorded event with activeMinutes = 45"
        def body = """
        {
            "events": [{
                "idempotencyKey": "minutes|test|${System.currentTimeMillis()}",
                "type": "ActiveMinutesRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "activeMinutes": 45,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with activeMinutes"
        eventRepository.findAll()[0].payload.activeMinutes == 45
    }
}

