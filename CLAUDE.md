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

# Run only integration tests
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

### Modular Architecture by Feature
The codebase follows a modular architecture organized by feature/bounded context:

**Package Structure**:
- `appevents/` - App-facing health events submission API
- `healthevents/` - Core event storage and management (event log, validation)
- `dailysummary/` - Daily aggregated summaries from health events
- `steps/` - Steps tracking projections and queries
- `workout/` - Workout projections and queries
- `workoutimport/` - Workout import from external sources
- `sleep/` - Sleep data projections and queries
- `calories/` - Calories tracking projections
- `activity/` - Activity minutes projections
- `meals/` - Meal tracking projections and queries
- `mealimport/` - Meal import from external sources
- `googlefit/` - Google Fit synchronization and OAuth
- `assistant/` - AI health assistant with Gemini integration (SSE streaming)
- `security/` - HMAC authentication filters
- `config/` - Spring configuration and global exception handling

**Key Pattern**: Each module has:
- Internal implementation classes (package-private)
- Public `api/` subpackage with facade interfaces and DTOs
- Modules communicate only through facades (dependency inversion)

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

**Calories Projection** (`CaloriesProjector.java`):
- Input: `ActiveCaloriesBurnedRecorded.v1` events
- Output: `calories_hourly_projections` → `calories_daily_projections`

**Activity Projection** (`ActivityProjector.java`):
- Input: `ActiveMinutesRecorded.v1` events
- Output: `activity_hourly_projections` → `activity_daily_projections`

**Meals Projection** (`MealsProjector.java`):
- Input: `MealRecorded.v1` events
- Output: `meal_projections`

**Important**: Projections are eventually consistent. Projection failures don't block event ingestion.

### AI Health Assistant (Spring AI + Gemini)

**Architecture**: Natural language interface for querying health data using Gemini 2.0 Flash with SSE streaming.

**Flow**:
1. POST /v1/assistant/chat → `AssistantController` (SSE endpoint)
2. → `AssistantService.streamChat()` builds dynamic system instruction with current date
3. → `ChatClient` (Spring AI) streams to Gemini with function calling enabled
4. → AI decides which tools to call based on user question
5. → `HealthTools` executes tool calls (delegates to facades)
6. → Results streamed back as SSE events: `ContentEvent`, `ToolCallEvent`, `ToolResultEvent`, `DoneEvent`

**Key Features**:
- **Conversation History**: Multi-turn conversations with context retention (last 20 messages)
- **Smart Date Recognition**: AI automatically converts natural language ("dzisiaj", "ostatni tydzień", "ostatni miesiąc") to ISO-8601 dates
- **Dynamic System Prompt**: Current date injected into prompt so AI can calculate relative dates
- **5 Tools Available**: `getStepsData`, `getSleepData`, `getWorkoutData`, `getDailySummary`, `getMealsData`
- **SSE Streaming**: Real-time word-by-word responses via Server-Sent Events
- **Context Management**: `AssistantContext` uses ThreadLocal to pass deviceId to tools

**Key Files**:
- `AssistantService.java` - Main orchestrator, builds dynamic system instruction with `LocalDate.now()`, manages conversation flow
- `AssistantController.java` - SSE endpoint returning `Flux<ServerSentEvent<String>>`
- `ConversationService.java` - Manages conversation history, loads/saves messages, builds message lists
- `HealthTools.java` - Spring AI `@Tool` annotated methods for function calling
- `AssistantConfiguration.java` - ChatClient bean configuration
- `Conversation.java`, `ConversationMessage.java` - JPA entities for conversation storage

**Important Implementation Details**:
- System instruction must include "BIEŻĄCA DATA: {date}" at the top for date recognition to work
- All tool parameter descriptions specify ISO-8601 format requirement (YYYY-MM-DD)
- Tools retrieve deviceId from `AssistantContext.getDeviceId()` (set by controller)
- Errors are caught and returned as `ErrorEvent` to keep SSE stream alive

**Conversation History**:
- **Storage**: PostgreSQL tables `conversations` and `conversation_messages`
- **History Limit**: Last 20 messages (10 user/assistant exchanges) sent to Gemini
- **Message Flow**:
  1. Get/create conversation via `ConversationService.getOrCreateConversation()`
  2. Load history: `ConversationService.loadConversationHistory(conversationId, 20)`
  3. Build message list: `ConversationService.buildMessageList(history, systemPrompt, userMessage)`
  4. Save user message before streaming
  5. Collect assistant response during stream (StringBuilder in doOnNext)
  6. Save assistant response in doFinally after stream completes
  7. Return conversationId in DoneEvent
- **Security**: Conversations verified to belong to deviceId (prevents cross-device access)
- **Tool Calls**: NOT saved in history (only user/assistant text)
- **Backward Compatibility**: ChatRequest.conversationId is optional (null = new conversation)

**Configuration**:
```bash
export GOOGLE_GEMINI_API_KEY="your-api-key"
export GOOGLE_GEMINI_MODEL="gemini-2.0-flash-exp"  # optional, this is default
```

See `AI_ASSISTANT_README.md` for detailed documentation on date recognition patterns and conversation history.

### Health Data Push Model (Health Connect)

**Architecture**: The app uses a PUSH model where health data is submitted directly from Health Connect on the mobile device via `POST /v1/health-events`. The previous Google Fit polling model has been removed.

**Google Fit Module** (Legacy/Optional):
- Manual sync endpoints still available for historical backfill
- `POST /v1/google-fit/sync/history?days=N` - Historical sync (1-365 days)
- Uses virtual threads (Project Loom) for parallel processing
- Each day synced independently via `HistoricalSyncTask`

**Key Files**: `GoogleFitSyncService.java`, `HistoricalSyncTaskProcessor.java`

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
- `conversations` - AI assistant conversation tracking (V8)
- `conversation_messages` - Message history per conversation (V8)
- `historical_sync_tasks` - Track historical sync jobs (V12)

**Projection Tables**:
- `steps_hourly_projections`, `steps_daily_projections` (V6)
- `workout_projections`, `workout_exercise_projections`, `workout_set_projections` (V5)
- `sleep_projections` (V7)
- `calories_hourly_projections`, `calories_daily_projections` (V10)
- `activity_hourly_projections`, `activity_daily_projections` (V11)
- `meal_projections` (V13)

**Migrations**: Flyway versioned in `src/main/resources/db/migration/` (V1-V14)

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
| `MealRecorded.v1` | `title`, `mealType`, `caloriesKcal`, `proteinGrams`, `fatGrams`, `carbohydratesGrams`, `healthRating` | macros ≥ 0, valid enums |

**Meal Types**: `BREAKFAST`, `BRUNCH`, `LUNCH`, `DINNER`, `SNACK`, `DESSERT`, `DRINK`

**Health Ratings**: `VERY_HEALTHY`, `HEALTHY`, `NEUTRAL`, `UNHEALTHY`, `VERY_UNHEALTHY`

See `EventValidator.java` for detailed validation rules.

## Testing

**Test Framework**: Spock (Groovy) with Spring Boot Test + Testcontainers

**Integration Tests** (`integration-tests/` module):
- 250+ Spock specifications
- PostgreSQL via Testcontainers
- REST Assured for API testing
- WireMock for mocking external APIs

**Test Spec Files**:
- Event validation: `StepsEventValidationSpec`, `SleepEventValidationSpec`, `WorkoutSpec`, `MealEventSpec`, `HeartRateEventSpec`, `DistanceEventSpec`, `WalkingSessionEventSpec`, `ActiveMinutesEventSpec`, `ActiveCaloriesEventSpec`
- Projections: `StepsProjectionSpec`, `SleepProjectionSpec`, `WorkoutProjectionSpec`, `CaloriesProjectionSpec`, `ActivityProjectionSpec`, `MealProjectionSpec`
- Features: `DailySummarySpec`, `AssistantSpec`, `ConversationHistorySpec`, `GoogleFitSyncSpec`
- Import: `WorkoutImportSpec`, `MealImportSpec`
- AI Evaluation: `evaluation/AiHallucinationSpec` (LLM-as-a-Judge tests)

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
- `GOOGLE_GEMINI_API_KEY` - Gemini API key for AI Assistant (required for /v1/assistant/chat)
- `GOOGLE_GEMINI_MODEL` - Gemini model name (default: gemini-2.0-flash-exp)
- `GOOGLE_FIT_CLIENT_ID` - Google OAuth client ID (for historical sync)
- `GOOGLE_FIT_CLIENT_SECRET` - Google OAuth client secret (for historical sync)
- `GOOGLE_FIT_REFRESH_TOKEN` - Google OAuth refresh token (for historical sync)

### Spring Features
- `@EnableScheduling` - Google Fit sync scheduled tasks
- `@EnableCaching` - Caffeine cache for nonce replay protection
- `@EnableFeignClients` - Google Fit API client
- `@EnableJpaRepositories` - Spring Data JPA
- Spring AI with Google Gemini - AI assistant with function calling

## API Endpoints

### AI Assistant
- `POST /v1/assistant/chat` - Chat with AI assistant (SSE streaming, HMAC auth required)
  - Request: `{"message": "Ile kroków zrobiłem dzisiaj?"}`
  - Response: SSE stream with `ContentEvent`, `ToolCallEvent`, `ToolResultEvent`, `DoneEvent`

### Event Ingestion
- `POST /v1/health-events` - Batch event ingestion (no auth)

### Daily Summaries
- `GET /v1/daily-summaries/{date}` - Get daily summary (HMAC auth required)
- `GET /v1/daily-summaries/range?from={date}&to={date}` - Get daily summary range (HMAC auth required)

### Google Fit Sync (Historical/Legacy)
- `POST /v1/google-fit/sync/history?days=N` - Historical sync (1-365 days)

### Query APIs
- `GET /v1/steps/daily/{date}` - Daily step breakdown
- `GET /v1/steps/daily/range?from={date}&to={date}` - Step range summary
- `GET /v1/workouts/{workoutId}` - Workout details
- `GET /v1/workouts/date/{date}` - Workouts on date
- `GET /v1/sleep/range?from={date}&to={date}` - Sleep data range
- `GET /v1/meals/range?from={date}&to={date}` - Meals data range

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
- **Spring AI 1.1.0** with Google Gemini integration
- **PostgreSQL 16** with JSONB
- **Gradle 8.5+** with Kotlin DSL
- **Flyway** for database migrations
- **Caffeine** for in-memory caching
- **Feign** for Google Fit API client
- **Testcontainers** for integration testing
- **Spock** for test specifications
- **Docker & Docker Compose** for containerization

## Additional Documentation

- **README.md** - Overview, quick start guide, and API documentation
