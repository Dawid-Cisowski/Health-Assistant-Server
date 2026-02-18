# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## TDD Workflow (MANDATORY)

**ALWAYS write integration tests FIRST, before writing any production code.**

1. Write Spock integration tests that define expected behavior
2. Run tests - they should FAIL (red)
3. Write minimal production code to make tests pass (green)
4. Refactor if needed

**No production code without tests. No exceptions.**

---

## Build & Development Commands

```bash
# Build (compilation + SpotBugs + PMD, no tests)
./gradlew build -x test

# Run main module tests (unit + modularity, no DB needed)
./gradlew :test

# Run integration tests (requires Docker for Testcontainers)
./gradlew :integration-tests:test

# Run specific integration test
./gradlew :integration-tests:test --tests "*BatchEventIngestionSpec"

# Run AI evaluation tests (requires GEMINI_API_KEY)
GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiMutationToolSpec"

# Run locally (requires PostgreSQL)
./gradlew bootRun

# Docker Compose (recommended)
docker-compose up --build
```

---

## CRITICAL: Code Quality Rules

### 1. NO Imperative Loops
**FORBIDDEN**: `for`, `while`, `do-while`. **REQUIRED**: Stream API, `datesUntil()`, `iterate()`.

### 2. NO @Setter on Entities
**FORBIDDEN**: `@Setter` on any JPA entity. **REQUIRED**: Business methods with meaningful names (Rich Domain Model).

### 3. @Version on ALL Entities
Every JPA entity MUST have `@Version private Long version;` for optimistic locking.

### 4. Protected No-Args Constructor
`@NoArgsConstructor(access = AccessLevel.PROTECTED)` on all JPA entities.

### 5. Log Sanitization
All user input in logs MUST use `maskDeviceId()` and `sanitizeForLog()`. Never log secrets, tokens, or full device IDs.

### 6. Safe Error Messages
Never expose internal details in API responses. Map exceptions to generic safe messages.

### 7. AI Prompt Injection Protection
Validate length and check for injection patterns before sending user input to AI models.

### 8. DTOs as Records, Value Objects for Domain Concepts
All DTOs should be Java Records. Use Value Objects (Records with validation) instead of primitive types for IDs, etc.

---

## Architecture Overview

### Tech Stack
- **Java 21** (virtual threads, records, sealed classes, pattern matching)
- **Spring Boot 4.0.2** + **Spring AI 2.0.0-M2** (Gemini) + **Spring Modulith 2.0.1**
- **PostgreSQL 16** (JSONB) + **Flyway** (V1-V48)
- **Spock** (Groovy) + **Testcontainers** + **REST Assured** + **WireMock**
- **SpotBugs + PMD + JaCoCo** for code quality

### Modular Architecture (23 modules, verified by Spring Modulith)

Each module has `api/` public subpackage (facade + DTOs) and package-private internals:
```
module/
  api/              # PUBLIC - facade interface + DTOs (Records)
  ModuleEntity.java # INTERNAL (package-private)
  ModuleService.java
  ModuleRepository.java
  ModuleController.java
```

**Modules**: appevents, healthevents, dailysummary, steps, workout, workoutimport, sleep, sleepimport, calories, activity, meals, mealimport, weight, weightimport, heartrate, bodymeasurements, googlefit, assistant, guardrails, notifications, reports, security, config

**Key Rules**:
- Only `api/` subpackage classes are public
- Cross-module communication ONLY through facade interfaces
- Controllers are internal to the module

### Event-Driven Design

All health data flows through an immutable event log (`health_events` table):

1. `POST /v1/health-events` -> `HealthEventsFacade.submitEvents()`
2. -> `StoreHealthEventsCommandHandler.handle()` validates, stores (idempotent via `idempotency_key`)
3. -> **After transaction commits**: publishes domain events, projects to materialized views, aggregates daily summaries
4. -> Returns per-event status (stored/duplicate/invalid)

**Important**: Events published AFTER transaction commit via `TransactionSynchronizationManager.afterCommit()`.

### Projection Pattern

Events are projected into query-optimized views (eventually consistent):
- Steps: hourly -> daily projections
- Workout: workout -> exercise -> set projections
- Calories/Activity: hourly -> daily
- Meals, Weight, Sleep, HeartRate, BodyMeasurements: direct projections

All projection entities use `@Version` with retry on `ObjectOptimisticLockingFailureException`.

### AI Health Assistant (Spring AI + Gemini)

Natural language interface with **18 tools** (11 read + 7 mutation):

**Read tools**: `getStepsData`, `getSleepData`, `getWorkoutData`, `getDailySummary`, `getDailySummaryRange`, `getMealsData`, `getMealsDailyDetail`, `getWeightData`, `getBodyMeasurementsData`, `getBodyPartHistory`, `getEnergyRequirements`

**Mutation tools**: `recordMeal`, `updateMeal`, `deleteMeal`, `recordWeight`, `recordWorkout`, `deleteWorkout`, `recordSleep`

**Key files**:
- `AssistantService.java` - orchestrator, builds dynamic system instruction with current date
- `HealthTools.java` - Spring AI `@Tool` methods, delegates to facades
- `ConversationService.java` - conversation history (last 20 messages)

**Implementation details**:
- Tools get deviceId from `ToolContext` (Spring AI), NOT ThreadLocal
- System instruction must include current date for relative date recognition
- All tool parameters are Strings (Spring AI + Gemini limitation), parsed internally
- SSE streaming: `ContentEvent`, `ToolCallEvent`, `ToolResultEvent`, `DoneEvent`

### HMAC Authentication

All `/v1/*` endpoints require HMAC-SHA256 signature.

**Headers**: `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`

**Signature**: `HMAC-SHA256(METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY, device_secret)`

### Time Zones
- Storage: UTC (`TIMESTAMPTZ`)
- Daily boundaries: Poland time (`Europe/Warsaw`) for aggregations

---

## Gotchas: Adding New Modules / Endpoints

When adding a new module, you MUST update these files:

1. **`ModularityTests.java`** - add module name to `shouldDetectAllExpectedModules` expected set
2. **`ArchitectureRulesTest.java`** - add package to controller allowlist if module has a controller
3. **`HmacAuthenticationFilter.java`** - add path pattern to `requiresAuthentication()` if new `/v1/*` endpoint
4. **Flyway migration** - include `version` column in any new projection table

Other gotchas:
- Integration tests (77 specs) all fail without Docker/Testcontainers - this is normal
- PMD has a pre-existing false positive on `projectionModulesShouldNotDependOnImportModules`
- `@ConditionalOnProperty` required for optional features (AI, notifications)

---

## Event Types

| Event Type | Key Payload Fields |
|---|---|
| `StepsBucketedRecorded.v1` | `bucketStart`, `bucketEnd`, `count` |
| `HeartRateSummaryRecorded.v1` | `bucketStart`, `bucketEnd`, `avg`, `min`, `max`, `samples` |
| `SleepSessionRecorded.v1` | `sleepStart`, `sleepEnd`, `totalMinutes` |
| `ActiveCaloriesBurnedRecorded.v1` | `bucketStart`, `bucketEnd`, `energyKcal` |
| `ActiveMinutesRecorded.v1` | `bucketStart`, `bucketEnd`, `activeMinutes` |
| `DistanceBucketedRecorded.v1` | `bucketStart`, `bucketEnd`, `distanceMeters` |
| `WalkingSessionRecorded.v1` | `sessionId`, `start`, `end`, `durationMinutes` |
| `WorkoutRecorded.v1` | `workoutId`, `performedAt`, `exercises[]` |
| `MealRecorded.v1` | `title`, `mealType`, `caloriesKcal`, `proteinGrams`, `fatGrams`, `carbohydratesGrams`, `healthRating` |
| `WeightMeasured.v1` | `weightKg`, `measuredAt` |
| `RestingHeartRateRecorded.v1` | `measuredAt`, `beatsPerMinute` |
| `BodyMeasurementRecorded.v1` | `measuredAt`, `bodyFatPercent`, `muscleMassKg`, etc. |

**Meal Types**: `BREAKFAST`, `BRUNCH`, `LUNCH`, `DINNER`, `SNACK`, `DESSERT`, `DRINK`
**Health Ratings**: `VERY_HEALTHY`, `HEALTHY`, `NEUTRAL`, `UNHEALTHY`, `VERY_UNHEALTHY`

See `EventValidator.java` for detailed validation rules.

---

## API Endpoints

### AI Assistant
- `POST /v1/assistant/chat` - Chat (SSE streaming, supports mutation commands)

### Event Ingestion
- `POST /v1/health-events` - Batch event ingestion

### Daily Summaries
- `GET /v1/daily-summaries/{date}` - Daily summary
- `GET /v1/daily-summaries/range?from=&to=` - Range summary
- `GET /v1/daily-summaries/{date}/ai-report` - AI daily health report
- `GET /v1/daily-summaries/range/ai-report?startDate=&endDate=` - AI range report

### Query APIs
- `GET /v1/steps/daily/{date}` | `GET /v1/steps/daily/range?from=&to=`
- `GET /v1/workouts/{workoutId}` | `GET /v1/workouts?from=&to=`
- `GET /v1/sleep/range?from=&to=`
- `GET /v1/meals/range?from=&to=`
- `GET /v1/weight/latest` | `GET /v1/weight/range?from=&to=`
- `GET /v1/heartrate/range?from=&to=`
- `GET /v1/exercises/{exerciseId}/statistics?from=&to=`

### Import APIs (AI-powered, multipart)
- `POST /v1/sleep/import-image`, `/v1/workouts/import-image`, `/v1/meals/import-image`, `/v1/weight/import-image`

### Other
- `POST /v1/notifications/fcm-token` | `DELETE /v1/notifications/fcm-token`
- `POST /v1/google-fit/sync/day?date=YYYY-MM-DD` (legacy backfill)
- `GET /actuator/health` | `GET /actuator/prometheus` | `GET /swagger-ui.html`

---

## Configuration

### Required
- `DB_URL`, `DB_USER`, `DB_PASSWORD` - PostgreSQL
- `HMAC_DEVICES_JSON` - `'{"device-id":"base64-secret"}'`

### Optional
- `GEMINI_API_KEY` - for AI assistant + imports
- `GEMINI_MODEL` - default: `gemini-3-flash-preview`
- `app.notifications.enabled` - FCM push (default: false)
- `GOOGLE_FIT_CLIENT_ID/SECRET/REFRESH_TOKEN` - legacy sync

---

## Common Development Patterns

### Adding a New Event Type
1. Define payload DTO in `dto/payload/` (implement `EventPayload`)
2. Add validation in `EventValidator.validatePayload()`
3. Add to `EventType` enum
4. Update `DailySummaryAggregator` if affects daily summaries
5. Create projector if needed
6. Add integration tests

### Adding a New Projection
1. Flyway migration (include `version` column!)
2. JPA entity (with `@Version`, no `@Setter`, `@NoArgsConstructor(access = PROTECTED)`)
3. Projector class wired into `StoreHealthEventsCommandHandler.projectEvents()`
4. Query endpoints + facade
5. Integration tests
