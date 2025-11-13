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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved without stage details"
        eventRepository.findAll().size() == 1
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
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
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with activeMinutes"
        eventRepository.findAll()[0].payload.activeMinutes == 45
    }

    // ========== ExerciseSessionRecorded.v1 Tests ==========

    def "Scenario 2.14: Valid ExerciseSessionRecorded event"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with all fields"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|test|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "e4210819-5708-3835-bcbb-2776e037e258",
                    "type": "other_0",
                    "start": "2025-11-10T09:03:15Z",
                    "end": "2025-11-10T10:03:03Z",
                    "durationMinutes": 59,
                    "distanceMeters": "5838.7",
                    "steps": 13812,
                    "avgSpeedMetersPerSecond": "1.65",
                    "avgHr": 83,
                    "maxHr": 123,
                    "originPackage": "com.heytap.health.international"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved with all fields"
        def events = eventRepository.findAll()
        events.size() == 1
        def event = events[0]
        event.eventType == "ExerciseSessionRecorded.v1"
        event.payload.sessionId == "e4210819-5708-3835-bcbb-2776e037e258"
        event.payload.type == "WALK"
        event.payload.durationMinutes == 59
        event.payload.steps == 13812
        event.payload.avgHr == 83
        event.payload.maxHr == 123
    }

    def "Scenario 2.15: ExerciseSessionRecorded event with minimal required fields"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with only required fields"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|minimal|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved successfully"
        eventRepository.findAll().size() == 1
    }

    def "Scenario 2.16: Missing sessionId field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event without sessionId"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|missing|sessionId|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: sessionId")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.17: Missing type field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event without type"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|missing|type|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: type")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.18: Missing start field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event without start"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|missing|start|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: start")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.19: Missing end field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event without end"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|missing|end|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "durationMinutes": 60,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: end")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.20: Missing durationMinutes field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event without durationMinutes"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|missing|duration|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Missing required field: durationMinutes")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.21: Missing originPackage field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event without originPackage"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|missing|origin|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
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

    def "Scenario 2.22: Negative durationMinutes value"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with durationMinutes = -10"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|negative|duration|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": -10,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
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

    def "Scenario 2.23: Negative steps value"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with steps = -100"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|negative|steps|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "steps": -100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
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

    def "Scenario 2.24: Negative avgHr value"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with avgHr = -50"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|negative|avghr|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "avgHr": -50,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
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

    def "Scenario 2.25: Negative maxHr value"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with maxHr = -100"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|negative|maxhr|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "maxHr": -100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
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

    def "Scenario 2.26: ExerciseSessionRecorded event with null avgHr and maxHr (optional fields)"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "ExerciseSessionRecorded event with null avgHr and maxHr"
        def body = """
        {
            "events": [{
                "idempotencyKey": "exercise|null|hr|${System.currentTimeMillis()}",
                "type": "ExerciseSessionRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "sessionId": "test-session-id",
                    "type": "other_0",
                    "start": "2025-11-10T09:00:00Z",
                    "end": "2025-11-10T10:00:00Z",
                    "durationMinutes": 60,
                    "avgHr": null,
                    "maxHr": null,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result status should be 'stored'"
        response.body().jsonPath().getString("results[0].status") == "stored"

        and: "event saved successfully with null HR values"
        eventRepository.findAll().size() == 1
    }
}

