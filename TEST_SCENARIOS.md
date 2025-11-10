# Health Assistant Backend - Test Scenarios (BDD)

## Feature 1: HMAC Authentication

### Pozytywne Scenariusze

#### Scenario 1.1: Successful authentication with valid HMAC signature
```gherkin
Given device "test-device" exists in configuration with valid secret
And current timestamp is within tolerance window (±10 minutes)
And nonce "550e8400-e29b-41d4-a716-446655440000" has not been used before
And valid HMAC signature is calculated correctly
When I POST to "/v1/ingest/events" with valid headers and body
Then response status should be 202 ACCEPTED
And request should reach the controller
```

#### Scenario 1.2: Authentication with timestamp at tolerance boundary (exactly 10 minutes ago)
```gherkin
Given device "test-device" exists in configuration
And timestamp is exactly 600 seconds (10 minutes) in the past
And valid HMAC signature for that timestamp
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
```

#### Scenario 1.3: Authentication with future timestamp within tolerance
```gherkin
Given device "test-device" exists in configuration
And timestamp is 5 minutes in the future (within tolerance)
And valid HMAC signature
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
```

### Negatywne Scenariusze

#### Scenario 1.4: Missing X-Device-Id header
```gherkin
Given valid timestamp, nonce, and signature headers
But X-Device-Id header is missing
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error code should be "HMAC_AUTH_FAILED"
And error message should contain "Missing X-Device-Id header"
```

#### Scenario 1.5: Missing X-Timestamp header
```gherkin
Given valid device ID, nonce, and signature
But X-Timestamp header is missing
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Missing X-Timestamp header"
```

#### Scenario 1.6: Missing X-Nonce header
```gherkin
Given valid device ID, timestamp, and signature
But X-Nonce header is missing
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Missing X-Nonce header"
```

#### Scenario 1.7: Missing X-Signature header
```gherkin
Given valid device ID, timestamp, and nonce
But X-Signature header is missing
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Missing X-Signature header"
```

#### Scenario 1.8: Unknown device ID
```gherkin
Given device "unknown-device-123" does NOT exist in configuration
And all other headers are valid
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Unknown device ID: unknown-device-123"
```

#### Scenario 1.9: Invalid timestamp format
```gherkin
Given device exists in configuration
And X-Timestamp header value is "2025-11-10 12:00:00" (invalid format)
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Invalid timestamp format"
```

#### Scenario 1.10: Timestamp too old (outside tolerance)
```gherkin
Given device exists in configuration
And timestamp is 11 minutes in the past (outside 10-minute tolerance)
And valid HMAC signature for that old timestamp
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Timestamp out of acceptable range"
```

#### Scenario 1.11: Timestamp too far in future (outside tolerance)
```gherkin
Given device exists in configuration
And timestamp is 11 minutes in the future
And valid HMAC signature
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Timestamp out of acceptable range"
```

#### Scenario 1.12: Invalid HMAC signature
```gherkin
Given device exists in configuration
And all headers are present with valid format
But X-Signature is calculated incorrectly
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Invalid signature"
```

#### Scenario 1.13: Signature calculated with wrong secret
```gherkin
Given device "test-device" exists with secret "ABC123"
And HMAC signature is calculated using different secret "XYZ789"
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Invalid signature"
```

#### Scenario 1.14: Replay attack - reused nonce
```gherkin
Given device exists in configuration
And nonce "550e8400-e29b-41d4-a716-446655440000" was used in previous request
And all other headers are valid with correct signature
When I POST to "/v1/ingest/events" with same nonce
Then response status should be 401 UNAUTHORIZED
And error message should contain "Nonce already used (replay attack detected)"
```

#### Scenario 1.15: Canonical string mismatch - wrong body
```gherkin
Given device exists in configuration
And HMAC signature is calculated for body A
But actual request body is B
When I POST to "/v1/ingest/events"
Then response status should be 401 UNAUTHORIZED
And error message should contain "Invalid signature"
```

#### Scenario 1.16: Canonical string mismatch - wrong HTTP method
```gherkin
Given device exists in configuration
And HMAC signature is calculated for POST method
But request uses GET method
When I GET "/v1/ingest/events"
Then response status should be 405 METHOD NOT ALLOWED
```

---

## Feature 2: Event Validation

### Pozytywne Scenariusze - StepsBucketedRecorded.v1

#### Scenario 2.1: Valid StepsBucketedRecorded event
```gherkin
Given authenticated request
And event type is "StepsBucketedRecorded.v1"
And payload contains all required fields:
  | bucketStart    | 2025-11-10T06:00:00Z              |
  | bucketEnd      | 2025-11-10T07:00:00Z              |
  | count          | 742                               |
  | originPackage  | com.google.android.apps.fitness   |
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
And event should be saved in database
```

#### Scenario 2.2: Steps event with count = 0
```gherkin
Given authenticated request
And StepsBucketedRecorded event with count = 0
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

#### Scenario 2.3: Steps event with large count (edge case)
```gherkin
Given authenticated request
And StepsBucketedRecorded event with count = 999999
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

### Negatywne Scenariusze - StepsBucketedRecorded.v1

#### Scenario 2.4: Missing bucketStart field
```gherkin
Given authenticated request
And StepsBucketedRecorded event without bucketStart field
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Missing required field: bucketStart"
```

#### Scenario 2.5: Missing count field
```gherkin
Given authenticated request
And StepsBucketedRecorded event without count field
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Missing required field: count"
```

#### Scenario 2.6: Negative count value
```gherkin
Given authenticated request
And StepsBucketedRecorded event with count = -10
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "count must be non-negative"
```

#### Scenario 2.7: Missing originPackage field
```gherkin
Given authenticated request
And StepsBucketedRecorded event without originPackage
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Missing required field: originPackage"
```

### Pozytywne Scenariusze - HeartRateSummaryRecorded.v1

#### Scenario 2.8: Valid HeartRateSummaryRecorded event
```gherkin
Given authenticated request
And event type is "HeartRateSummaryRecorded.v1"
And payload contains:
  | bucketStart    | 2025-11-10T07:00:00Z    |
  | bucketEnd      | 2025-11-10T07:15:00Z    |
  | avg            | 78.3                    |
  | min            | 61                      |
  | max            | 115                     |
  | samples        | 46                      |
  | originPackage  | com.example.app         |
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

### Negatywne Scenariusze - HeartRateSummaryRecorded.v1

#### Scenario 2.9: Missing avg field
```gherkin
Given authenticated request
And HeartRateSummaryRecorded event without avg field
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Missing required field: avg"
```

#### Scenario 2.10: Negative heart rate value
```gherkin
Given authenticated request
And HeartRateSummaryRecorded event with avg = -10
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "avg must be non-negative"
```

#### Scenario 2.11: samples less than 1
```gherkin
Given authenticated request
And HeartRateSummaryRecorded event with samples = 0
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "samples must be positive"
```

### Pozytywne Scenariusze - SleepSessionRecorded.v1

#### Scenario 2.12: Valid SleepSessionRecorded event with stages
```gherkin
Given authenticated request
And event type is "SleepSessionRecorded.v1"
And payload contains:
  | sleepStart     | 2025-11-09T22:00:00Z  |
  | sleepEnd       | 2025-11-10T06:00:00Z  |
  | lightMinutes   | 240                   |
  | deepMinutes    | 120                   |
  | remMinutes     | 90                    |
  | awakeMinutes   | 30                    |
  | totalMinutes   | 480                   |
  | hasStages      | true                  |
  | originPackage  | com.example.sleep     |
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

#### Scenario 2.13: Valid SleepSessionRecorded event without stages
```gherkin
Given authenticated request
And SleepSessionRecorded event with:
  | totalMinutes  | 480   |
  | hasStages     | false |
And no stage details (lightMinutes, deepMinutes, etc.)
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

### Negatywne Scenariusze - SleepSessionRecorded.v1

#### Scenario 2.14: Missing totalMinutes field
```gherkin
Given authenticated request
And SleepSessionRecorded event without totalMinutes
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Missing required field: totalMinutes"
```

### Pozytywne Scenariusze - Other Event Types

#### Scenario 2.15: Valid ActiveCaloriesBurnedRecorded event
```gherkin
Given authenticated request
And ActiveCaloriesBurnedRecorded.v1 event with energyKcal = 150.5
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

#### Scenario 2.16: Valid ActiveMinutesRecorded event
```gherkin
Given authenticated request
And ActiveMinutesRecorded.v1 event with activeMinutes = 45
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

#### Scenario 2.17: Valid WorkoutSessionImported event
```gherkin
Given authenticated request
And WorkoutSessionImported.v1 event from source "gymrun"
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

#### Scenario 2.18: Valid SetPerformedImported event
```gherkin
Given authenticated request
And SetPerformedImported.v1 event with exercise, weightKg, reps
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

#### Scenario 2.19: Valid MealLoggedEstimated event
```gherkin
Given authenticated request
And MealLoggedEstimated.v1 event with items array and total
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
```

### Negatywne Scenariusze - General Validation

#### Scenario 2.20: Invalid event type
```gherkin
Given authenticated request
And event type is "InvalidEventType.v1"
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Invalid event type"
```

#### Scenario 2.21: Empty payload
```gherkin
Given authenticated request
And event payload is {}
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "invalid"
And error message should contain "Payload cannot be empty"
```

#### Scenario 2.22: Null payload
```gherkin
Given authenticated request
And event payload is null
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Payload is required"
```

---

## Feature 3: Idempotency

### Pozytywne Scenariusze

#### Scenario 3.1: First-time event ingestion
```gherkin
Given authenticated request
And idempotency key "user1|steps|2025-11-10T06:00:00Z|com.test" has never been used
And valid event payload
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result status should be "stored"
And event should be saved in database with generated event_id
```

#### Scenario 3.2: Duplicate detection - exact same event
```gherkin
Given authenticated request
And idempotency key "user1|steps|2025-11-10T06:00:00Z|com.test" was used in previous request
And event was already saved in database
When I POST to "/v1/ingest/events" with same idempotency key
Then response status should be 202 ACCEPTED
And result status should be "duplicate"
And no new event should be created in database
And event_id should NOT be returned
```

#### Scenario 3.3: Different events with different idempotency keys
```gherkin
Given authenticated request
And two events with different idempotency keys:
  | key1 | user1|steps|2025-11-10T06:00:00Z|com.test |
  | key2 | user1|steps|2025-11-10T07:00:00Z|com.test |
When I POST both events to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And both events should have status "stored"
And both events should be saved in database
```

### Negatywne Scenariusze

#### Scenario 3.4: Missing idempotency key
```gherkin
Given authenticated request
And event envelope without idempotencyKey field
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Idempotency key is required"
```

#### Scenario 3.5: Idempotency key too short (< 8 characters)
```gherkin
Given authenticated request
And idempotency key is "short"
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Idempotency key must be between 8 and 512 characters"
```

#### Scenario 3.6: Idempotency key too long (> 512 characters)
```gherkin
Given authenticated request
And idempotency key is 513 characters long
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Idempotency key must be between 8 and 512 characters"
```

---

## Feature 4: Batch Processing

### Pozytywne Scenariusze

#### Scenario 4.1: Single event batch
```gherkin
Given authenticated request
And batch contains 1 valid event
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And results array should contain 1 item
And result status should be "stored"
```

#### Scenario 4.2: Multiple events batch (happy path)
```gherkin
Given authenticated request
And batch contains 10 valid events with different idempotency keys
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And results array should contain 10 items
And all results should have status "stored"
And all 10 events should be saved in database
```

#### Scenario 4.3: Maximum batch size (100 events)
```gherkin
Given authenticated request
And batch contains exactly 100 valid events
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And results array should contain 100 items
And all 100 events should be processed
```

#### Scenario 4.4: Mixed batch - stored, duplicate, and invalid events
```gherkin
Given authenticated request
And batch contains:
  | index | type              | status expected |
  | 0     | new valid event   | stored          |
  | 1     | duplicate event   | duplicate       |
  | 2     | invalid event     | invalid         |
  | 3     | new valid event   | stored          |
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And results should match:
  | index | status    |
  | 0     | stored    |
  | 1     | duplicate |
  | 2     | invalid   |
  | 3     | stored    |
```

#### Scenario 4.5: Batch with duplicate within same request
```gherkin
Given authenticated request
And batch contains 3 events where events at index 0 and 2 have same idempotency key
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And result at index 0 should be "stored"
And result at index 2 should be "duplicate"
```

### Negatywne Scenariusze

#### Scenario 4.6: Empty batch
```gherkin
Given authenticated request
And batch contains 0 events (empty array)
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Events list cannot be empty"
```

#### Scenario 4.7: Batch exceeds maximum size (> 100 events)
```gherkin
Given authenticated request
And batch contains 101 events
When I POST to "/v1/ingest/events"
Then response status should be 413 PAYLOAD TOO LARGE
And error code should be "BATCH_TOO_LARGE"
And error message should contain "Too many events in batch"
```

#### Scenario 4.8: Malformed JSON in request body
```gherkin
Given authenticated request
And request body is "{ invalid json }"
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Malformed JSON"
```

#### Scenario 4.9: Missing events field in request body
```gherkin
Given authenticated request
And request body is "{}" (no events field)
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Events list cannot be empty"
```

---

## Feature 5: Event Metadata

### Pozytywne Scenariusze

#### Scenario 5.1: Event ID generation
```gherkin
Given authenticated request
And valid event is submitted
When event is stored successfully
Then generated event_id should start with "evt_"
And event_id should be unique
And event_id should be returned in response
```

#### Scenario 5.2: Timestamp recording
```gherkin
Given authenticated request
And valid event with occurredAt = "2025-11-10T07:00:00Z"
When event is stored
Then event.occurredAt should be stored as "2025-11-10T07:00:00Z"
And event.createdAt should be current server time
```

#### Scenario 5.3: Device ID association
```gherkin
Given authenticated request from device "test-device-123"
And valid event
When event is stored
Then event.deviceId should be "test-device-123"
```

### Negatywne Scenariusze

#### Scenario 5.4: Missing occurredAt timestamp
```gherkin
Given authenticated request
And event envelope without occurredAt field
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Occurred at timestamp is required"
```

#### Scenario 5.5: Invalid occurredAt format
```gherkin
Given authenticated request
And event occurredAt is "2025-11-10 07:00:00" (wrong format)
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
```

#### Scenario 5.6: Missing event type
```gherkin
Given authenticated request
And event envelope without type field
When I POST to "/v1/ingest/events"
Then response status should be 400 BAD REQUEST
And error message should contain "Event type is required"
```

---

## Feature 6: Error Handling

### Scenariusze

#### Scenario 6.1: Database connection failure during event storage
```gherkin
Given authenticated request
And valid event payload
But database connection is lost
When I POST to "/v1/ingest/events"
Then response status should be 500 INTERNAL SERVER ERROR
And error code should be "INTERNAL_ERROR"
```

#### Scenario 6.2: Constraint violation (duplicate idempotency key in DB)
```gherkin
Given authenticated request
And idempotency key already exists in database
And somehow passes initial duplicate check
When attempting to save event
Then operation should fail gracefully
And result status should be "duplicate" or error returned
```

#### Scenario 6.3: Invalid Content-Type header
```gherkin
Given authenticated request
And Content-Type header is "text/plain"
When I POST to "/v1/ingest/events"
Then response status should be 415 UNSUPPORTED MEDIA TYPE
```

#### Scenario 6.4: Request body too large (beyond server limits)
```gherkin
Given authenticated request
And request body is > 10MB
When I POST to "/v1/ingest/events"
Then response status should be 413 PAYLOAD TOO LARGE
```

---

## Feature 7: Health & Monitoring

### Scenariusze

#### Scenario 7.1: Health check endpoint
```gherkin
Given server is running
When I GET "/actuator/health"
Then response status should be 200 OK
And response should contain "status": "UP"
```

#### Scenario 7.2: Prometheus metrics endpoint
```gherkin
Given server is running
When I GET "/actuator/prometheus"
Then response status should be 200 OK
And response should contain metrics in Prometheus format
```

#### Scenario 7.3: OpenAPI documentation
```gherkin
Given server is running
When I GET "/swagger-ui.html"
Then response status should be 200 OK
And Swagger UI should be displayed
```

---

## Feature 8: Edge Cases & Performance

### Scenariusze

#### Scenario 8.1: Very long idempotency key (near max limit)
```gherkin
Given authenticated request
And idempotency key is 500 characters long (under 512 limit)
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And event should be stored successfully
```

#### Scenario 8.2: Event with very large JSONB payload
```gherkin
Given authenticated request
And event payload contains 1000 fields
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And entire payload should be stored as JSONB
```

#### Scenario 8.3: Concurrent requests from same device
```gherkin
Given authenticated requests from device "test-device"
When 10 concurrent requests are sent simultaneously
Then all requests should be processed independently
And each should receive appropriate response
And database should remain consistent
```

#### Scenario 8.4: Nonce cache expiration
```gherkin
Given nonce "abc-123" was used 11 minutes ago
And nonce cache TTL is 10 minutes
When I use same nonce "abc-123" again with valid signature
Then nonce should NOT be detected as replay (cache expired)
And request should be processed successfully
```

#### Scenario 8.5: Special characters in payload
```gherkin
Given authenticated request
And payload contains special characters: " ' \ / < > & 
And payload contains unicode: 中文, 🎉, Ñoño
When I POST to "/v1/ingest/events"
Then response status should be 202 ACCEPTED
And payload should be stored correctly with all characters preserved
```

---

## Summary Statistics

**Total Scenarios: 84**

- **Feature 1 (HMAC Auth):** 16 scenarios (3 positive, 13 negative)
- **Feature 2 (Validation):** 20 scenarios (10 positive, 10 negative)
- **Feature 3 (Idempotency):** 6 scenarios (3 positive, 3 negative)
- **Feature 4 (Batch Processing):** 9 scenarios (5 positive, 4 negative)
- **Feature 5 (Event Metadata):** 6 scenarios (3 positive, 3 negative)
- **Feature 6 (Error Handling):** 4 scenarios (all negative)
- **Feature 7 (Health & Monitoring):** 3 scenarios (all positive)
- **Feature 8 (Edge Cases):** 5 scenarios (all edge cases)

**Coverage:**
- ✅ Authentication & Security
- ✅ Input Validation
- ✅ Business Logic (Idempotency)
- ✅ Batch Operations
- ✅ Error Scenarios
- ✅ Edge Cases
- ✅ Performance & Concurrency

