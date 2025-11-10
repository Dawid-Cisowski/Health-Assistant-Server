# Integration Tests

This module contains integration tests for the Health Assistant Backend.

## Overview

Integration tests verify the entire application stack including:
- HMAC authentication flow
- Event ingestion and validation
- Database persistence (PostgreSQL via Testcontainers)
- REST API endpoints
- Error handling

## Technology Stack

- **Spock Framework** - BDD-style testing with Groovy
- **Testcontainers** - PostgreSQL database for tests
- **REST Assured** - API testing
- **Spring Boot Test** - Spring context loading

## Running Tests

### Run all integration tests:
```bash
./gradlew :integration-tests:test
```

### Run specific test class:
```bash
./gradlew :integration-tests:test --tests "HmacAuthenticationSpec"
```

### Run with detailed output:
```bash
./gradlew :integration-tests:test --info
```

## Test Structure

```
integration-tests/
├── src/test/groovy/com/healthassistant/
│   ├── BaseIntegrationSpec.groovy      # Base class for all tests
│   ├── HmacAuthenticationSpec.groovy   # HMAC auth tests (11 scenarios)
│   ├── EventIngestionSpec.groovy       # Event ingestion tests (13 scenarios)
│   ├── EventValidationSpec.groovy      # Payload validation tests (13 scenarios)
│   └── ErrorHandlingSpec.groovy        # Error handling tests (9 scenarios)
└── src/test/resources/
    └── application-test.yml             # Test configuration
```

## Test Coverage

### Feature 1: HMAC Authentication (11 tests)
- Valid signature
- Invalid signature
- Unknown device
- Expired timestamp
- Replay attack (duplicate nonce)
- Missing headers

### Feature 2: Event Validation (13 tests)
- StepsBucketedRecorded.v1
- HeartRateSummaryRecorded.v1
- SleepSessionRecorded.v1
- ActiveCaloriesBurnedRecorded.v1
- ActiveMinutesRecorded.v1

### Feature 4: Batch Processing (13 tests)
- Single/multiple events
- Idempotency
- Partial success
- Batch size limits
- Malformed JSON

### Feature 6: Error Handling (9 tests)
- Missing required fields
- Invalid formats
- Wrong Content-Type
- Edge cases

## Test Reports

After running tests, view the HTML report at:
```
integration-tests/build/reports/tests/test/index.html
```

## Configuration

Tests use `application-test.yml` which configures:
- Testcontainers PostgreSQL database
- Test HMAC devices
- Logging levels
- Flyway migrations

## Notes

- Tests automatically start a PostgreSQL container using Testcontainers
- Each test cleans the database before/after execution
- Tests run in isolation and can be executed in parallel
- No mocks - all tests use real components

