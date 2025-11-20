# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Build & Test
```bash
# Build entire project (main + integration tests)
./gradlew build

# Build without tests
./gradlew build -x test

# Run only main module tests (if any exist)
./gradlew test

# Run only integration tests (46 Spock tests)
./gradlew :integration-tests:test

# Clean build
./gradlew clean build

# View test report
open integration-tests/build/reports/tests/test/index.html
```

### Running the Application
```bash
# Run locally (requires PostgreSQL running)
./gradlew bootRun

# Using Docker Compose (recommended for local development)
docker-compose up --build

# Stop Docker services
docker-compose down
```

### Database Operations
```bash
# Start PostgreSQL in Docker for local development
docker run --name postgres-dev \
  -e POSTGRES_DB=health_assistant \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:16-alpine

# Stop PostgreSQL
docker stop postgres-dev

# Remove PostgreSQL container
docker rm postgres-dev
```

## Architecture Overview

### Layered Architecture
The codebase follows a clean architecture with clear separation:

```
Infrastructure Layer (web, security, external APIs)
    ↓
Application Layer (facades, command handlers, services)
    ↓
Domain Layer (events, validators, value objects)
    ↓
Data Layer (JPA entities, repositories)
```

**Package Structure**:
- `infrastructure/` - REST controllers, security filters, Google Fit client
- `application/` - Business logic orchestration (ingestion, summary, sync)
- `domain/` - Core domain model (event, summary)
- `config/` - Spring configuration
- `dto/` - Data transfer objects for API

### Event-Driven Design

**Core Principle**: All health data flows through an immutable event log (`health_events` table).

**Event Ingestion Flow**:
1. POST /v1/health-events → `HealthEventsController`
2. → `HealthEventsFacade.submitEvents()`
3. → `StoreHealthEventsCommandHandler.handle()`
4. → Validate events (`EventValidator`)
5. → Store/update events via `EventRepository` (idempotency via `idempotency_key`)
6. → Project to materialized views (`StepsProjector`, `WorkoutProjector`)
7. → Aggregate daily summaries (`DailySummaryAggregator`)
8. → Return per-event status (stored/duplicate/invalid)

**Key Files**:
- `StoreHealthEventsCommandHandler.java` - Main orchestrator for event ingestion
- `EventValidator.java` - Type-specific payload validation
- `DailySummaryAggregator.java` - Generates daily summaries from events

### Projection Pattern

Events are projected into query-optimized views:

**Steps Projection** (`StepsProjector.java`):
- Input: `StepsBucketedRecorded.v1` events
- Output: `steps_hourly_projections` → `steps_daily_projections`
- Purpose: Fast queries for step totals, hourly breakdowns, active hours

**Workout Projection** (`WorkoutProjector.java`):
- Input: `WorkoutRecorded.v1` events
- Output: `workout_projections` → `workout_exercise_projections` → `workout_set_projections`
- Purpose: Pre-calculated workout metrics (volume, max weights)

**Important**: Projections are eventually consistent. Projection failures don't block event ingestion.

### Google Fit Synchronization

**Scheduled Sync**: Runs every 15 minutes via `@Scheduled(cron = "0 */15 * * * *")`

**Flow**:
1. `GoogleFitSyncService.performSync()` checks `last_synced_at`
2. Fetches data with 1-hour overlap buffer (catches delayed wearable uploads)
3. Maps Google Fit data to events:
   - Aggregate buckets → Steps/Distance/Calories/HeartRate events
   - Sleep sessions → SleepSessionRecorded events
   - Walking sessions → WalkingSessionRecorded events
4. Submits events via `HealthEventsFacade` (normal ingestion path)
5. Updates `google_fit_sync_state.last_synced_at`

**Historical Sync**: Manual trigger for backfilling data
- POST /v1/google-fit/sync/history?days=N
- Uses virtual threads (Project Loom) for parallel processing
- Each day synced independently

**Key File**: `GoogleFitSyncService.java`

### HMAC Authentication

**Protected Endpoints**: `GET /v1/daily-summaries/{date}` requires HMAC signature

**Headers Required**:
- `X-Device-Id`: Device identifier
- `X-Timestamp`: ISO-8601 UTC timestamp
- `X-Nonce`: Unique random string (prevents replay attacks)
- `X-Signature`: HMAC-SHA256 signature

**Signature Calculation**:
```
canonical = METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY
signature = HMAC-SHA256(canonical, device_secret)
```

**Validation** (in `HmacAuthenticationFilter.java`):
1. Check device exists in `HMAC_DEVICES_JSON` config
2. Verify nonce not used (cached with TTL)
3. Validate timestamp within tolerance (default ±600s)
4. Compute expected signature
5. Constant-time comparison

**Configuration**:
```bash
export HMAC_DEVICES_JSON='{"device-id":"base64-encoded-secret"}'
export HMAC_TOLERANCE_SEC=600
export NONCE_CACHE_TTL_SEC=600
```

### Idempotency

**Idempotency Key**:
- Client-provided: Used directly
- Auto-generated: `{deviceId}|{eventType}|{timestamp}-{index}`
- Workouts: `{deviceId}|workout|{workoutId}`

**Behavior**:
- First submission: Event stored, returns `stored`
- Duplicate key: Event **updated** with new payload, returns `duplicate`
- Allows correction of previously submitted events

**Database**: `idempotency_key` column with non-unique index (V4 migration removed unique constraint)

### Time Zone Handling

**Storage**: All timestamps stored in UTC (PostgreSQL `TIMESTAMPTZ`)

**Daily Boundaries**: Poland time zone (`Europe/Warsaw`) for aggregations
- Configured in `DailySummaryAggregator.POLAND_ZONE`
- Matters for determining which date an event belongs to

### Database Schema

**Core Tables**:
- `health_events` - Append-only event log (JSONB payload, GIN indexed)
- `daily_summaries` - Aggregated daily metrics (JSONB summary)
- `google_fit_sync_state` - Sync cursor (last_synced_at timestamp)

**Projection Tables** (V5, V6):
- `steps_hourly_projections`, `steps_daily_projections`
- `workout_projections`, `workout_exercise_projections`, `workout_set_projections`

**Migrations**: Flyway versioned in `src/main/resources/db/migration/`

## Event Types

All events have:
- `event_type` (String): Event type identifier
- `occurred_at` (Instant): When event occurred
- `device_id` (String): Source device
- `idempotency_key` (String): Deduplication key

### Supported Event Types

| Event Type | Key Fields | Validation |
|---|---|---|
| `StepsBucketedRecorded.v1` | `bucketStart`, `bucketEnd`, `count` | count ≥ 0 |
| `HeartRateSummaryRecorded.v1` | `bucketStart`, `bucketEnd`, `avg`, `min`, `max`, `samples` | metrics ≥ 0, samples > 0 |
| `SleepSessionRecorded.v1` | `sleepStart`, `sleepEnd`, `totalMinutes` | totalMinutes ≥ 0 |
| `ActiveCaloriesBurnedRecorded.v1` | `bucketStart`, `bucketEnd`, `energyKcal` | energyKcal ≥ 0 |
| `ActiveMinutesRecorded.v1` | `bucketStart`, `bucketEnd`, `activeMinutes` | activeMinutes ≥ 0 |
| `DistanceBucketedRecorded.v1` | `bucketStart`, `bucketEnd`, `distanceMeters` | distanceMeters ≥ 0 |
| `WalkingSessionRecorded.v1` | `sessionId`, `start`, `end`, `durationMinutes` | duration ≥ 0 |
| `WorkoutRecorded.v1` | `workoutId`, `performedAt`, `exercises[]` | exercises non-empty |

See `EventValidator.java` for detailed validation rules.

## Testing

**Test Framework**: Spock (Groovy) with Spring Boot Test + Testcontainers

**Integration Tests** (`integration-tests/` module):
- 46 Spock specifications
- PostgreSQL via Testcontainers
- REST Assured for API testing
- WireMock for mocking Google Fit API

**Test Categories**:
- HMAC authentication (11 tests)
- Event validation (13 tests)
- Batch processing (13 tests)
- Error handling (9 tests)

**Running Tests**:
```bash
# All tests
./gradlew :integration-tests:test

# Specific test class
./gradlew :integration-tests:test --tests "*BatchEventIngestionSpec"

# View report
open integration-tests/build/reports/tests/test/index.html
```

## Configuration

### Required Environment Variables
- `DB_URL` - PostgreSQL JDBC URL
- `DB_USER`, `DB_PASSWORD` - Database credentials
- `HMAC_DEVICES_JSON` - JSON map of device secrets (required for HMAC endpoints)

### Optional Environment Variables
- `HMAC_TOLERANCE_SEC` - Timestamp tolerance (default: 600)
- `NONCE_CACHE_TTL_SEC` - Nonce cache TTL (default: 600)
- `GOOGLE_FIT_CLIENT_ID` - Google OAuth client ID
- `GOOGLE_FIT_CLIENT_SECRET` - Google OAuth client secret
- `GOOGLE_FIT_REFRESH_TOKEN` - Google OAuth refresh token

### Spring Features
- `@EnableScheduling` - Google Fit sync scheduled tasks
- `@EnableCaching` - Caffeine cache for nonce replay protection
- `@EnableFeignClients` - Google Fit API client
- `@EnableJpaRepositories` - Spring Data JPA

## API Endpoints

### Event Ingestion
- `POST /v1/health-events` - Batch event ingestion (no auth)

### Daily Summaries
- `GET /v1/daily-summaries/{date}` - Get daily summary (HMAC auth required)

### Google Fit Sync
- `POST /v1/google-fit/sync` - Manual sync trigger
- `POST /v1/google-fit/sync/history?days=N` - Historical sync (1-365 days)

### Query APIs
- `GET /v1/steps/daily/{date}` - Daily step breakdown
- `GET /v1/steps/daily/range?from={date}&to={date}` - Step range summary
- `GET /v1/workouts/{workoutId}` - Workout details
- `GET /v1/workouts/date/{date}` - Workouts on date

### Monitoring
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Prometheus metrics
- `GET /swagger-ui.html` - OpenAPI documentation

## Key Design Principles

1. **Events are source of truth** - All data in immutable event log
2. **Projections are regenerable** - Can be rebuilt from events
3. **Idempotency guaranteed** - Safe to retry all operations
4. **No blocking failures** - Projection errors don't block ingestion
5. **Transactional operations** - `@Transactional` ensures atomicity
6. **Type safety** - Value objects, enums, validation at boundaries
7. **Time zone consistency** - UTC internally, Poland time for daily boundaries

## Common Development Patterns

### Adding a New Event Type

1. Define payload DTO in `dto/payload/` (implement `EventPayload`)
2. Add validation logic in `EventValidator.validatePayload()`
3. Add event type to `EventType` enum
4. Update `DailySummaryAggregator` if it affects daily summaries
5. Create projector if needed (e.g., `StepsProjector`, `WorkoutProjector`)
6. Add integration tests

### Adding a New Projection

1. Create Flyway migration for projection tables
2. Create projector class (e.g., `StepsProjector`)
3. Wire into `StoreHealthEventsCommandHandler.projectEvents()`
4. Create JPA entities and repositories
5. Create query endpoints in REST controller
6. Add integration tests

### Debugging Event Ingestion

Check logs for:
- `StoreHealthEventsCommandHandler` - Main ingestion flow
- `EventValidator` - Validation failures
- `StepsProjector`, `WorkoutProjector` - Projection errors
- `DailySummaryAggregator` - Aggregation issues

Query database:
```sql
-- Recent events
SELECT event_id, event_type, occurred_at, idempotency_key
FROM health_events
ORDER BY created_at DESC
LIMIT 10;

-- Events for specific date
SELECT * FROM health_events
WHERE occurred_at >= '2025-01-15'
  AND occurred_at < '2025-01-16';

-- Daily summary
SELECT * FROM daily_summaries WHERE date = '2025-01-15';
```

## Tech Stack

- **Java 21** with virtual threads (Project Loom)
- **Spring Boot 3.3.5** (Web, Data JPA, Actuator, Validation)
- **PostgreSQL 16** with JSONB
- **Gradle 8.5+** with Kotlin DSL
- **Flyway** for database migrations
- **Caffeine** for in-memory caching
- **Feign** for Google Fit API client
- **Testcontainers** for integration testing
- **Spock** for test specifications
- **Docker & Docker Compose** for containerization
