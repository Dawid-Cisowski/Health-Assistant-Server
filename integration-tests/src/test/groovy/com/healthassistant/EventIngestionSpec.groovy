package com.healthassistant

import spock.lang.Title

/**
 * Integration tests for Event Ingestion (Feature 1)
 */
@Title("Feature 1: Event Ingestion")
class EventIngestionSpec extends BaseIntegrationSpec {

    def "Scenario 2.1: Successfully ingest single event"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "single event payload"
        def idempotencyKey = "user1|steps|${System.currentTimeMillis()}|single"
        def body = createStepsEvent(idempotencyKey)

        when: "I POST event to /v1/health-events"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "response should contain single result"
        def responseBody = response.body().jsonPath()
        responseBody.getList("results").size() == 1
        responseBody.getString("results[0].status") == "stored"
        responseBody.getInt("results[0].index") == 0
        responseBody.getString("results[0].eventId") != null

        and: "event should be in database"
        def events = eventRepository.findAll()
        events.size() == 1
        events[0].idempotencyKey == idempotencyKey
    }

    def "Scenario 2.2: Successfully ingest multiple events in batch"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "batch of 3 different events"
        def timestamp = System.currentTimeMillis()
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "batch|steps|${timestamp}|1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T09:00:00Z",
                        "bucketEnd": "2025-11-10T10:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "batch|hr|${timestamp}|2",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-10T10:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T10:00:00Z",
                        "bucketEnd": "2025-11-10T10:15:00Z",
                        "avg": 78.5,
                        "min": 62,
                        "max": 115,
                        "samples": 46,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "batch|steps|${timestamp}|3",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T11:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T10:00:00Z",
                        "bucketEnd": "2025-11-10T11:00:00Z",
                        "count": 1523,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        when: "I POST batch to /v1/health-events"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "response should contain 3 results"
        def responseBody = response.body().jsonPath()
        responseBody.getList("results").size() == 3

        and: "all results should be 'stored'"
        (0..2).each { index ->
            assert responseBody.getString("results[${index}].status") == "stored"
            assert responseBody.getInt("results[${index}].index") == index
            assert responseBody.getString("results[${index}].eventId") != null
        }

        and: "all 3 events should be in database"
        def events = eventRepository.findAll()
        events.size() == 3
        events.collect { it.eventType }.sort() == [
            "HeartRateSummaryRecorded.v1",
            "StepsBucketedRecorded.v1",
            "StepsBucketedRecorded.v1"
        ].sort()
    }

    def "Scenario 2.3: Idempotency - duplicate event is skipped"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with specific idempotency key"
        def idempotencyKey = "user1|steps|${System.currentTimeMillis()}|duplicate"
        def body = createStepsEvent(idempotencyKey)

        when: "I send event first time"
        def response1 = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "first request succeeds"
        response1.statusCode() == 202
        response1.body().jsonPath().getString("results[0].status") == "stored"
        def firstEventId = response1.body().jsonPath().getString("results[0].eventId")

        when: "I send EXACT same event again (same idempotency key)"
        def response2 = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "second request is accepted but event is skipped"
        response2.statusCode() == 202
        
        and: "response indicates duplicate"
        def result = response2.body().jsonPath()
        result.getString("results[0].status") == "duplicate"
        result.getInt("results[0].index") == 0

        and: "only ONE event in database (not two)"
        eventRepository.findAll().size() == 1
    }

    def "Scenario 2.4: Partial success - some events stored, some duplicates"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "first event is already in database"
        def timestamp = System.currentTimeMillis()
        def existingKey = "partial|steps|${timestamp}|existing"
        def existingBody = createStepsEvent(existingKey)
        def existingResponse = authenticatedRequest(deviceId, secretBase64, existingBody)
                .post("/v1/health-events")
                .then()
                .extract()
        def existingEventId = existingResponse.body().jsonPath().getString("results[0].eventId")

        when: "I send batch with mix of new and duplicate events"
        def batchBody = """
        {
            "events": [
                {
                    "idempotencyKey": "${existingKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T09:00:00Z",
                        "bucketEnd": "2025-11-10T10:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "partial|steps|${timestamp}|new",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T11:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T10:00:00Z",
                        "bucketEnd": "2025-11-10T11:00:00Z",
                        "count": 1234,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        def response = authenticatedRequest(deviceId, secretBase64, batchBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response is 202 ACCEPTED (partial success)"
        response.statusCode() == 202

        and: "first event is marked as duplicate"
        def responseBody = response.body().jsonPath()
        responseBody.getString("results[0].status") == "duplicate"
        responseBody.getInt("results[0].index") == 0

        and: "second event is stored"
        responseBody.getString("results[1].status") == "stored"
        responseBody.getString("results[1].eventId") != null

        and: "total of 2 events in database (1 existing + 1 new)"
        eventRepository.findAll().size() == 2
    }

    def "Scenario 2.5: Validation error - invalid event type"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with INVALID type"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "invalid|type|${System.currentTimeMillis()}",
                    "type": "InvalidEventType.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "someField": "someValue"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        when: "I POST event with invalid type"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates validation error"
        response.statusCode() == 202 // Still 202 but with invalid status in results

        and: "result shows validation failure"
        def responseBody = response.body().jsonPath()
        responseBody.getString("results[0].status") == "invalid"
        responseBody.getString("results[0].error.message").contains("Invalid event type")

        and: "no events stored in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.6: Validation error - missing required fields in payload"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "StepsBucketedRecorded event missing 'count' field"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "invalid|payload|${System.currentTimeMillis()}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T09:00:00Z",
                        "bucketEnd": "2025-11-10T10:00:00Z",
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        when: "I POST event with invalid payload"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates validation error"
        response.statusCode() == 202

        and: "result shows missing required field"
        def responseBody = response.body().jsonPath()
        responseBody.getString("results[0].status") == "invalid"
        responseBody.getString("results[0].error.message").contains("Missing required field: count")

        and: "no events stored in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.7: Mixed batch - some valid, some invalid"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "batch with valid and invalid events"
        def timestamp = System.currentTimeMillis()
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "mixed|valid|${timestamp}|1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T09:00:00Z",
                        "bucketEnd": "2025-11-10T10:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "mixed|invalid|${timestamp}|2",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T11:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T10:00:00Z",
                        "bucketEnd": "2025-11-10T11:00:00Z",
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "mixed|valid|${timestamp}|3",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-10T11:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T11:00:00Z",
                        "bucketEnd": "2025-11-10T11:15:00Z",
                        "avg": 72.3,
                        "min": 60,
                        "max": 95,
                        "samples": 30,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        when: "I POST mixed batch"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response is 202 ACCEPTED"
        response.statusCode() == 202

        and: "first event is stored"
        def responseBody = response.body().jsonPath()
        responseBody.getString("results[0].status") == "stored"

        and: "second event has error (missing count)"
        responseBody.getString("results[1].status") == "invalid"
        responseBody.getString("results[1].error.message").contains("Missing required field: count")

        and: "third event is stored"
        responseBody.getString("results[2].status") == "stored"

        and: "only valid events (2) are in database"
        def events = eventRepository.findAll()
        events.size() == 2
        events.collect { it.eventType }.sort() == [
            "HeartRateSummaryRecorded.v1",
            "StepsBucketedRecorded.v1"
        ].sort()
    }

    def "Scenario 2.8: Successfully ingest HeartRateSummaryRecorded event"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "heart rate event payload"
        def idempotencyKey = "user1|hr|${System.currentTimeMillis()}|test"
        def body = createHeartRateEvent(idempotencyKey)

        when: "I POST heart rate event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "event is stored"
        def responseBody = response.body().jsonPath()
        responseBody.getString("results[0].status") == "stored"

        and: "event is in database with correct payload"
        def events = eventRepository.findAll()
        events.size() == 1
        def event = events[0]
        event.eventType == "HeartRateSummaryRecorded.v1"
        event.payload.avg == 78.3
        event.payload.min == 61
        event.payload.max == 115
        event.payload.samples == 46
    }

    def "Scenario 2.9: Empty batch is rejected"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "empty events array"
        def body = """
        {
            "events": []
        }
        """.stripIndent().trim()

        when: "I POST empty batch"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates payload too large (size validation)"
        response.statusCode() == 413

        and: "no events in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 2.10: Batch size limit is enforced"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "batch exceeding max size (assuming max is 100)"
        def timestamp = System.currentTimeMillis()
        def events = (1..101).collect { index ->
            """
            {
                "idempotencyKey": "large|batch|${timestamp}|${index}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": ${index * 100},
                    "originPackage": "com.google.android.apps.fitness"
                }
            }
            """
        }.join(",")

        def body = """
        {
            "events": [${events}]
        }
        """.stripIndent().trim()

        when: "I POST oversized batch"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates payload too large"
        response.statusCode() == 413

        and: "error message mentions batch size"
        def responseBody = response.body().jsonPath()
        responseBody.getString("code") == "BATCH_TOO_LARGE"
        responseBody.getString("message").toLowerCase().contains("too many")

        and: "no events in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 4.1: Batch with duplicate within same request"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "batch contains 3 events where events at index 0 and 2 have same idempotency key"
        def timestamp = System.currentTimeMillis()
        def duplicateKey = "same|key|${timestamp}"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "${duplicateKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T09:00:00Z",
                        "bucketEnd": "2025-11-10T10:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "different|key|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T11:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T10:00:00Z",
                        "bucketEnd": "2025-11-10T11:00:00Z",
                        "count": 200,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "${duplicateKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T12:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T11:00:00Z",
                        "bucketEnd": "2025-11-10T12:00:00Z",
                        "count": 300,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        when: "I POST batch"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "result at index 0 should be 'stored'"
        def responseBody = response.body().jsonPath()
        responseBody.getString("results[0].status") == "stored"

        and: "result at index 1 should be 'stored'"
        responseBody.getString("results[1].status") == "stored"

        and: "result at index 2 should be 'duplicate' (same key as index 0)"
        responseBody.getString("results[2].status") == "duplicate"

        and: "only 2 events stored (index 0 and 1)"
        eventRepository.findAll().size() == 2
    }

    def "Scenario 4.2: Malformed JSON in request body"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "malformed JSON body"
        def malformedBody = "{ invalid json }"

        when: "I POST malformed JSON"
        def response = authenticatedRequest(deviceId, secretBase64, malformedBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST"
        response.statusCode() == 400

        and: "error message should indicate malformed JSON"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 4.3: Missing events field in request body"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "request body without events field"
        def body = "{}"

        when: "I POST body without events"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST"
        response.statusCode() == 400

        and: "error message should mention events list"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }
}

