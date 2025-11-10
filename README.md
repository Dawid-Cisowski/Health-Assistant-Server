# Health Assistant Event Collector - Event Ingestion API

A Spring Boot 3.3 backend service for ingesting normalized health events from mobile applications with HMAC authentication and idempotency guarantees.

## 🚀 Features

- **Batch Event Ingestion**: Accept up to 100 events per request
- **HMAC Authentication**: Secure header-based authentication with replay protection
- **Idempotency**: Automatic deduplication using client-provided keys
- **Append-Only Storage**: Events stored in PostgreSQL with JSONB payloads
- **Event Validation**: Type-specific payload validation
- **OpenAPI Documentation**: Interactive API docs via Swagger UI
- **Production Ready**: Docker support, health checks, metrics

## 📋 Tech Stack

- **Java 21** (Eclipse Temurin)
- **Spring Boot 3.3.5**
- **PostgreSQL 16** with JSONB support
- **Gradle (Kotlin DSL)**
- **Flyway** for database migrations
- **Caffeine** for nonce caching (anti-replay)
- **Testcontainers** for integration testing
- **Docker & Docker Compose** for containerization

## 🏗️ Project Structure

```
health-assistant-event-collector/
├── src/
│   ├── main/
│   │   ├── java/com/healthassistant/
│   │   │   ├── config/           # Configuration classes
│   │   │   ├── controller/       # REST controllers
│   │   │   ├── domain/           # JPA entities
│   │   │   ├── dto/              # Data transfer objects
│   │   │   ├── repository/       # Spring Data repositories
│   │   │   ├── security/         # HMAC authentication
│   │   │   └── service/          # Business logic
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/     # Flyway migrations
├── integration-tests/            # Integration tests module (46 tests)
│   ├── src/test/groovy/
│   │   └── com/healthassistant/
│   │       ├── BaseIntegrationSpec.groovy
│   │       ├── HmacAuthenticationSpec.groovy
│   │       ├── EventIngestionSpec.groovy
│   │       ├── EventValidationSpec.groovy
│   │       └── ErrorHandlingSpec.groovy
│   ├── build.gradle.kts
│   └── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── Dockerfile
└── README.md
```

## 🔧 Configuration

Configure via environment variables:

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/health_assistant` | - |
| `DB_USER` | Database username | `postgres` | - |
| `DB_PASSWORD` | Database password | `postgres` | - |
| `HMAC_DEVICES_JSON` | JSON map of device ID to base64 secret | `{"test-device-1":"dGVzdC1zZWNyZXQtMTIz"}` | See below |
| `HMAC_TOLERANCE_SEC` | Timestamp tolerance in seconds | `600` | `300` |
| `NONCE_CACHE_TTL_SEC` | Nonce cache TTL (anti-replay) | `600` | `900` |

### HMAC Devices Configuration

Format: JSON object mapping device IDs to base64-encoded secrets

```json
{
  "device-1": "dGVzdC1zZWNyZXQtMTIz",
  "device-2": "YW5vdGhlci1zZWNyZXQ="
}
```

To generate a base64 secret:
```bash
echo -n "my-secret-key" | base64
```

## 🚀 Quick Start

### Using Docker Compose (Recommended)

1. **Start services**:
   ```bash
   docker-compose up --build
   ```

2. **Access the API**:
   - API: http://localhost:8080/v1/ingest/events
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health: http://localhost:8080/actuator/health
   - Metrics: http://localhost:8080/actuator/prometheus

3. **Stop services**:
   ```bash
   docker-compose down
   ```

### Local Development

**Prerequisites**:
- Java 21
- PostgreSQL 16
- Gradle 8.5+

**Steps**:

1. **Start PostgreSQL**:
   ```bash
   docker run --name postgres-dev \
     -e POSTGRES_DB=health_assistant \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 \
     -d postgres:16-alpine
   ```

2. **Set environment variables**:
   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/health_assistant
   export DB_USER=postgres
   export DB_PASSWORD=postgres
   export HMAC_DEVICES_JSON='{"test-device":"dGVzdC1zZWNyZXQtMTIz"}'
   ```

3. **Run application**:
   ```bash
   ./gradlew bootRun
   ```

## 📡 API Usage

### Authentication

All requests to `/v1/ingest/*` require HMAC authentication via headers:

```
X-Device-Id: test-device
X-Timestamp: 2025-11-09T07:05:12Z
X-Nonce: 550e8400-e29b-41d4-a716-446655440000
X-Signature: <HMAC-SHA256-base64>
```

### Canonical String Format

The signature is computed over a canonical string (newline-separated):

```
POST
/v1/ingest/events
2025-11-09T07:05:12Z
550e8400-e29b-41d4-a716-446655440000
test-device
{"events":[...]}
```

### Example Request

```bash
#!/bin/bash

DEVICE_ID="test-device"
SECRET="test-secret-123"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
NONCE=$(uuidgen)
BODY='{"events":[{"idempotencyKey":"user1|healthconnect|steps|1731129600000","type":"StepsBucketedRecorded.v1","occurredAt":"2025-11-09T07:00:00Z","payload":{"bucketStart":"2025-11-09T06:00:00Z","bucketEnd":"2025-11-09T07:00:00Z","count":742,"originPackage":"com.heytap.health.international"}}]}'

# Build canonical string
CANONICAL="POST
/v1/ingest/events
$TIMESTAMP
$NONCE
$DEVICE_ID
$BODY"

# Calculate HMAC (requires base64 secret)
SECRET_B64="dGVzdC1zZWNyZXQtMTIz"
SIGNATURE=$(echo -n "$CANONICAL" | openssl dgst -sha256 -hmac $(echo "$SECRET_B64" | base64 -d) -binary | base64)

# Send request
curl -X POST http://localhost:8080/v1/ingest/events \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: $DEVICE_ID" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Nonce: $NONCE" \
  -H "X-Signature: $SIGNATURE" \
  -d "$BODY"
```

### Response Format

**Success (202 Accepted)**:
```json
{
  "results": [
    {
      "index": 0,
      "status": "stored",
      "eventId": "evt_01HF3S3B7Z2Q9QZ8"
    },
    {
      "index": 1,
      "status": "duplicate"
    }
  ]
}
```

**Error (400/401)**:
```json
{
  "code": "HMAC_AUTH_FAILED",
  "message": "Invalid signature",
  "details": []
}
```

## 🧪 Testing

The project uses a separate `integration-tests` module with **46 integration tests** covering:
- HMAC authentication (11 tests)
- Event validation (13 tests)
- Batch processing (13 tests)
- Error handling (9 tests)

### Run all tests (main + integration):
```bash
./gradlew build
```

### Run only integration tests:
```bash
./gradlew :integration-tests:test
```

### Run specific test class:
```bash
./gradlew :integration-tests:test --tests "HmacAuthenticationSpec"
```

### View test reports:
```bash
open integration-tests/build/reports/tests/test/index.html
```

**Testing Stack:**
- Spock Framework (Groovy BDD)
- Testcontainers (PostgreSQL)
- REST Assured (API testing)
- No mocks - all tests use real components

See [integration-tests/README.md](integration-tests/README.md) for detailed testing documentation.

## 📊 Supported Event Types

| Event Type | Description | Status |
|------------|-------------|--------|
| `StepsBucketedRecorded.v1` | Bucketed step counts from health sources | ✅ Active |
| `HeartRateSummaryRecorded.v1` | Heart rate statistics over a time window | ✅ Active |
| `SleepSessionRecorded.v1` | Sleep session with optional stage breakdown | ✅ Active |
| `ActiveCaloriesBurnedRecorded.v1` | Active calories burned in a time bucket | ✅ Active |
| `ActiveMinutesRecorded.v1` | Active minutes in a time bucket | ✅ Active |
| `WorkoutSessionImported.v1` | Imported workout session (e.g., from GymRun) | 🚧 Coming soon |
| `SetPerformedImported.v1` | Individual exercise set performance | 🚧 Coming soon |
| `MealLoggedEstimated.v1` | Logged meal with nutritional estimates | 🚧 Coming soon |

See [OpenAPI spec](http://localhost:8080/swagger-ui.html) for detailed payload schemas.

## 🗄️ Database Schema

**Table: `health_events`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `event_id` | VARCHAR(32) | Server-generated unique ID (e.g., `evt_XXXX`) |
| `idempotency_key` | VARCHAR(512) | Client-provided deduplication key |
| `event_type` | VARCHAR(64) | Event type (e.g., `StepsBucketedRecorded.v1`) |
| `occurred_at` | TIMESTAMPTZ | When event occurred (client time) |
| `payload` | JSONB | Event-specific payload |
| `device_id` | VARCHAR(128) | Device that sent the event |
| `created_at` | TIMESTAMPTZ | Server ingestion timestamp |

**Indexes**:
- Unique: `idempotency_key`, `event_id`
- Regular: `occurred_at`, `event_type`, `created_at`, `device_id`
- GIN: `payload` (for future JSONB queries)

## 🔒 Security Considerations

1. **HMAC Secrets**: Store secrets securely (e.g., AWS Secrets Manager, HashiCorp Vault)
2. **HTTPS**: Always use HTTPS in production
3. **Timestamp Tolerance**: Adjust `HMAC_TOLERANCE_SEC` based on client clock accuracy
4. **Nonce Cache**: Size according to expected request volume
5. **Rate Limiting**: Add rate limiting for production (not included in phase 1)

## 📈 Monitoring

**Health Check**:
```bash
curl http://localhost:8080/actuator/health
```

**Prometheus Metrics**:
```bash
curl http://localhost:8080/actuator/prometheus
```

**Key Metrics**:
- `http_server_requests_seconds`: Request duration
- `jvm_memory_used_bytes`: Memory usage
- `jdbc_connections_active`: Active DB connections

## 🚧 Future Enhancements (Phase 2+)

- [ ] Materialized views/projections for analytics
- [ ] GraphQL API for flexible querying
- [ ] Event replay and reprocessing
- [ ] Rate limiting per device
- [ ] Webhook notifications
- [ ] Multi-region replication

## 📝 License

Copyright © 2025. All rights reserved.

## 🤝 Contributing

This is a private project. For questions or issues, contact the development team.

---

**Built with ❤️ using Spring Boot 3.3 and Java 21**

