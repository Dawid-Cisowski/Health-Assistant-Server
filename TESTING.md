# Testing Guide

## Test Structure

The project includes two types of tests:

1. **Unit Tests** - Fast, no external dependencies
2. **Integration Tests** - Require Docker (via Testcontainers)

## Running Tests

### All Tests (Requires Docker)

```bash
# Make sure Docker is running
docker ps

# Run all tests
./gradlew test
```

### Unit Tests Only (No Docker Required)

```bash
# Run only unit tests
./gradlew test --tests "*EventValidatorTest"
```

### Specific Test Class

```bash
# Run a specific test class
./gradlew test --tests "IngestControllerIntegrationTest"
```

### With Coverage Report

```bash
./gradlew test jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html
```

## Test Coverage

### Unit Tests ✅ (No Docker Required)

- **EventValidatorTest** (6 tests)
  - Valid event validation for all types
  - Missing field detection
  - Negative value validation
  - Invalid event type handling

### Integration Tests 🐳 (Requires Docker)

- **IngestControllerIntegrationTest** (6 tests)
  - Successful event ingestion
  - Duplicate detection
  - Invalid event type handling
  - HMAC authentication failure
  - Missing HMAC headers
  - Batch processing

## Testcontainers Requirements

Integration tests use [Testcontainers](https://www.testcontainers.org/) to spin up a PostgreSQL instance automatically.

**Requirements:**
- Docker Desktop (Mac/Windows) or Docker Engine (Linux)
- Docker must be running before test execution
- Sufficient disk space for container images (~200MB for postgres:16-alpine)

**First Run:**
- Tests will download the PostgreSQL image (one-time, ~1-2 minutes)
- Subsequent runs are faster (~10-20 seconds)

## Troubleshooting

### Docker Not Running

```
Error: java.lang.IllegalStateException: Could not find a valid Docker environment
```

**Solution:** Start Docker Desktop or Docker service
```bash
# Check if Docker is running
docker ps

# Mac: Start Docker Desktop from Applications
# Linux: sudo systemctl start docker
```

### Port Already in Use

```
Error: Container startup failed: port 5432 is already allocated
```

**Solution:** Stop services using port 5432
```bash
# Find process using port 5432
lsof -ti:5432 | xargs kill -9

# Or stop your local PostgreSQL
brew services stop postgresql  # Mac
sudo systemctl stop postgresql  # Linux
```

### Testcontainers Permission Denied

```
Error: Got permission denied while trying to connect to the Docker daemon socket
```

**Solution (Linux):**
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Log out and log back in
newgrp docker
```

### Disk Space Issues

```
Error: no space left on device
```

**Solution:** Clean up Docker
```bash
# Remove unused containers, images, networks
docker system prune -a

# Remove unused volumes
docker volume prune
```

## CI/CD Considerations

### GitHub Actions Example

```yaml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Setup Docker (for Testcontainers)
        uses: docker/setup-buildx-action@v2
      
      - name: Run Tests
        run: ./gradlew test
      
      - name: Upload Test Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: build/reports/tests/test/
```

### GitLab CI Example

```yaml
test:
  image: openjdk:21-jdk
  services:
    - docker:dind
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
  script:
    - ./gradlew test
  artifacts:
    when: always
    reports:
      junit: build/test-results/test/TEST-*.xml
```

## Manual API Testing

For manual testing without automated tests:

```bash
# 1. Start application
docker-compose up

# 2. Run test script
./test-api.sh

# 3. Manual curl example
curl -X POST http://localhost:8080/v1/ingest/events \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: test-device" \
  -H "X-Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  -H "X-Nonce: $(uuidgen)" \
  -H "X-Signature: <computed-hmac>" \
  -d '{"events":[{"idempotencyKey":"test-key","type":"StepsBucketedRecorded.v1","occurredAt":"2025-11-09T07:00:00Z","payload":{"bucketStart":"2025-11-09T06:00:00Z","bucketEnd":"2025-11-09T07:00:00Z","count":742,"originPackage":"com.test"}}]}'
```

## Test Data

### Valid Event Examples

See `test-api.sh` for complete examples of all event types.

### Invalid Examples

```json
// Invalid event type
{
  "events": [{
    "idempotencyKey": "test",
    "type": "InvalidType.v1",
    "occurredAt": "2025-11-09T07:00:00Z",
    "payload": {}
  }]
}

// Missing required field
{
  "events": [{
    "idempotencyKey": "test",
    "type": "StepsBucketedRecorded.v1",
    "occurredAt": "2025-11-09T07:00:00Z",
    "payload": {
      "bucketStart": "2025-11-09T06:00:00Z",
      "bucketEnd": "2025-11-09T07:00:00Z"
      // missing: count, originPackage
    }
  }]
}
```

## Performance Testing

For load testing, consider using:

- **JMeter** - Traditional load testing
- **Gatling** - Scala-based performance testing
- **k6** - Modern load testing tool

Example k6 script:

```javascript
import http from 'k6/http';

export default function () {
  const payload = JSON.stringify({
    events: [{
      idempotencyKey: `test-${Date.now()}`,
      type: 'StepsBucketedRecorded.v1',
      occurredAt: new Date().toISOString(),
      payload: {
        bucketStart: '2025-11-09T06:00:00Z',
        bucketEnd: '2025-11-09T07:00:00Z',
        count: 742,
        originPackage: 'com.test'
      }
    }]
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Device-Id': 'test-device',
      'X-Timestamp': new Date().toISOString(),
      'X-Nonce': crypto.randomUUID(),
      'X-Signature': '<compute-hmac>'
    },
  };

  http.post('http://localhost:8080/v1/ingest/events', payload, params);
}
```

## Summary

- ✅ Unit tests run without Docker
- 🐳 Integration tests require Docker + Testcontainers
- 📊 Use `./test-api.sh` for manual API testing
- 🚀 CI/CD pipelines need Docker support
- 📈 Add performance testing for production loads

---

**Need help?** See [README.md](README.md) for more information.

