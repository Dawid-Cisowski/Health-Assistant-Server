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

---

# Development Standards

## CRITICAL: Code Quality Rules

These rules MUST be followed for ALL code in this repository. Violations are NOT acceptable.

### 1. NO Imperative Loops

**FORBIDDEN**: `for`, `while`, `do-while` loops

**REQUIRED**: Stream API, `datesUntil()`, `iterate()`, functional methods

```java
// ❌ FORBIDDEN
for (int i = 0; i < items.size(); i++) {
    process(items.get(i));
}

while (!current.isAfter(endDate)) {
    results.add(process(current));
    current = current.plusDays(1);
}

// ✅ REQUIRED
items.forEach(this::process);

// ✅ REQUIRED - date iteration
startDate.datesUntil(endDate.plusDays(1))
    .map(this::process)
    .toList();

// ✅ REQUIRED - indexed iteration if index needed
IntStream.range(0, items.size())
    .mapToObj(i -> processWithIndex(items.get(i), i))
    .toList();
```

### 2. NO @Setter on Entities (Anemic Domain Model Prevention)

**FORBIDDEN**: `@Setter` on any JPA entity or domain object

**REQUIRED**: Business methods with meaningful names

```java
// ❌ FORBIDDEN - Anemic Domain Model
@Entity
@Getter
@Setter  // NEVER!
class WorkoutProjection {
    private int totalVolume;
    private int exerciseCount;
}

// ✅ REQUIRED - Rich Domain Model
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class WorkoutProjection {
    private int totalVolume;
    private int exerciseCount;

    // Business method - tells WHAT is happening
    void updateWorkoutMetrics(int totalVolume, int exerciseCount) {
        this.totalVolume = totalVolume;
        this.exerciseCount = exerciseCount;
    }
}
```

### 3. @Version on ALL Entities

**REQUIRED**: Every JPA entity MUST have optimistic locking

```java
@Entity
class SomeProjection {
    @Version
    private Long version;  // ALWAYS required

    // ... other fields
}
```

### 4. Protected No-Args Constructor

**REQUIRED**: JPA entities use `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

```java
// ❌ FORBIDDEN
@NoArgsConstructor
class Entity { }

// ✅ REQUIRED
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Entity { }
```

### 5. Log Sanitization

**REQUIRED**: All user input in logs MUST be sanitized

```java
// Helper methods required in controllers/services that log user data
private String maskDeviceId(String deviceId) {
    if (deviceId == null || deviceId.length() < 8) return "***";
    return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
}

private String sanitizeForLog(String input) {
    if (input == null) return "null";
    return input.replaceAll("[\\r\\n\\t]", "_");
}

// Usage
log.info("Request from device {}: {}", maskDeviceId(deviceId), sanitizeForLog(filename));
```

### 6. AI Prompt Injection Protection

**REQUIRED**: Validate all user input sent to AI models

```java
// Before sending to AI
private void validateUserInput(String message) {
    if (message == null || message.isBlank()) {
        throw new IllegalArgumentException("Message cannot be empty");
    }
    if (message.length() > 2000) {
        throw new IllegalArgumentException("Message too long");
    }
    // Check for prompt injection patterns
    String lower = message.toLowerCase();
    if (lower.contains("ignore previous") || lower.contains("system prompt")) {
        throw new IllegalArgumentException("Invalid message content");
    }
}
```

### 7. No Sensitive Data in Logs

**FORBIDDEN**: Logging secrets, tokens, full device IDs, personal data

```java
// ❌ FORBIDDEN
log.info("Processing request with secret: {}", apiKey);
log.info("User {} data: {}", userId, personalData);

// ✅ REQUIRED
log.info("Processing request for device {}", maskDeviceId(deviceId));
log.debug("Processing {} items", items.size());
```

### 8. Safe Error Messages

**REQUIRED**: Never expose internal details in API responses

```java
// ❌ FORBIDDEN
return ResponseEntity.badRequest().body(e.getMessage());

// ✅ REQUIRED
private String mapToSafeErrorMessage(String errorMessage) {
    if (errorMessage == null) return "Invalid request";
    if (errorMessage.contains("empty")) return "Input is empty";
    if (errorMessage.contains("size")) return "Input exceeds maximum size";
    return "Invalid request";
}
```

---

## Architecture Standards (DDD & Clean Architecture)

### Module Structure

Each module follows a flat structure with public `api/` subpackage:
```
module/
├── api/                    # PUBLIC - facades and DTOs only
│   ├── ModuleFacade.java   # Public interface (only way to access module)
│   └── dto/                # Public DTOs (Records preferred)
│       └── SomeResponse.java
├── ModuleEntity.java       # INTERNAL (package-private)
├── ModuleService.java      # INTERNAL (package-private)
├── ModuleRepository.java   # INTERNAL (package-private)
├── ModuleController.java   # INTERNAL (package-private)
└── ModuleProjector.java    # INTERNAL (package-private)
```

**Key Rules**:
- Only `api/` subpackage is public (facade + DTOs)
- Everything else is package-private (no `public` modifier)
- Other modules can ONLY access through the facade
- Controllers are internal to the module (not in `api/`)

### Facade Pattern

**REQUIRED**: All cross-module communication through facades

```java
// ✅ Public facade interface
public interface WorkoutFacade {
    WorkoutDto getWorkout(WorkoutId id, DeviceId deviceId);
    List<WorkoutDto> getWorkoutsInRange(LocalDate from, LocalDate to, DeviceId deviceId);
}

// ✅ Internal implementation (package-private)
@Service
@RequiredArgsConstructor
class WorkoutFacadeImpl implements WorkoutFacade {
    private final WorkoutService workoutService;
    // ...
}
```

### Value Objects

**REQUIRED**: Use Value Objects for domain concepts

```java
// ✅ Value Object as Record
public record DeviceId(String value) {
    public DeviceId {
        Objects.requireNonNull(value, "DeviceId cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("DeviceId cannot be blank");
    }

    public static DeviceId of(String value) {
        return new DeviceId(value);
    }
}

// ✅ Usage - type safety
void processEvent(DeviceId deviceId, EventId eventId) { }  // Clear types
// vs
void processEvent(String deviceId, String eventId) { }     // Confusing
```

### DTOs as Records

**REQUIRED**: All DTOs should be Java Records

```java
// ✅ Immutable DTO
public record WorkoutResponse(
    UUID workoutId,
    Instant performedAt,
    int durationMinutes,
    int totalVolume,
    List<ExerciseResponse> exercises
) { }
```

### Repository Pattern

```java
// ✅ Repository interface
interface WorkoutRepository extends JpaRepository<WorkoutEntity, UUID> {

    @Query("SELECT w FROM WorkoutEntity w WHERE w.deviceId = :deviceId AND w.date BETWEEN :from AND :to")
    List<WorkoutEntity> findByDeviceIdAndDateRange(
        @Param("deviceId") String deviceId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
```

---

## Modern Java 21 Patterns

### Required Java Features

1. **Records** for DTOs and Value Objects
2. **Sealed Classes** for type hierarchies
3. **Pattern Matching** for instanceof and switch
4. **Stream API** for all collection processing
5. **Optional** for nullable returns (never null)
6. **Virtual Threads** for I/O operations

### Pattern Matching Examples

```java
// ✅ Pattern matching for instanceof
if (payload instanceof StepsPayload steps) {
    processSteps(steps.count(), steps.bucketStart());
}

// ✅ Pattern matching in switch
return switch (eventType) {
    case STEPS -> processSteps(event);
    case SLEEP -> processSleep(event);
    case WORKOUT -> processWorkout(event);
    default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
};
```

### Stream API Patterns

```java
// ✅ Grouping
Map<LocalDate, List<Event>> eventsByDate = events.stream()
    .collect(Collectors.groupingBy(Event::getDate));

// ✅ Filtering and mapping
List<WorkoutDto> workouts = entities.stream()
    .filter(e -> e.getDeviceId().equals(deviceId))
    .map(this::toDto)
    .toList();

// ✅ Reduction
int totalSteps = hourlyData.stream()
    .mapToInt(HourlySteps::getCount)
    .sum();

// ✅ Optional handling
return repository.findById(id)
    .map(this::toDto)
    .orElseThrow(() -> new NotFoundException("Workout not found: " + id));
```

---

## Testing Standards

### Integration Tests (Spock + Testcontainers)

**REQUIRED**: All features must have integration tests

```groovy
class WorkoutProjectionSpec extends IntegrationSpec {

    def "should project workout with exercises and sets"() {
        given: "a workout event"
        def workoutEvent = createWorkoutEvent(deviceId, workoutId, exercises)

        when: "event is submitted"
        submitEvents([workoutEvent])

        then: "workout is projected"
        def workout = getWorkout(workoutId)
        workout.totalVolume == expectedVolume
        workout.exercises.size() == exercises.size()
    }

    def "should handle concurrent updates with optimistic locking"() {
        given: "existing workout projection"
        def existing = createProjection()

        when: "concurrent updates occur"
        def futures = (1..10).collect {
            CompletableFuture.runAsync { updateProjection(existing.id) }
        }
        CompletableFuture.allOf(futures as CompletableFuture[]).join()

        then: "no updates are lost"
        def result = getProjection(existing.id)
        result.version >= 10
    }
}
```

### Test Patterns

1. **Given-When-Then** structure
2. **One assertion per test** (or related assertions)
3. **Test edge cases**: null, empty, boundary values
4. **Test error cases**: validation failures, not found
5. **Test concurrency**: optimistic locking, race conditions

---

## Security Checklist

### Before Every PR

- [ ] No `@Setter` on entities
- [ ] No `for`/`while` loops (use Stream API)
- [ ] All entities have `@Version`
- [ ] All user input sanitized in logs
- [ ] No sensitive data in logs or error messages
- [ ] AI inputs validated for prompt injection
- [ ] HMAC authentication on all `/v1/*` endpoints
- [ ] Integration tests cover new functionality
- [ ] No hardcoded secrets or credentials

### Security Patterns

```java
// ✅ Constant-time comparison for secrets
MessageDigest.isEqual(expected.getBytes(), actual.getBytes());

// ✅ Input validation at boundaries
@PostMapping("/v1/events")
ResponseEntity<?> submitEvents(@Valid @RequestBody EventRequest request) {
    // @Valid triggers validation
}

// ✅ Output encoding
return ResponseEntity.ok(sanitizedResponse);
```

---

## Code Review Checklist

### Critical Issues (Must Fix)

1. **Imperative loops** → Replace with Stream API
2. **@Setter on entities** → Add business methods
3. **Missing @Version** → Add optimistic locking
4. **Log injection vulnerabilities** → Sanitize inputs
5. **Prompt injection risks** → Validate AI inputs
6. **Information disclosure** → Safe error messages
7. **Missing validation** → Add @Valid, null checks

### Warnings (Should Fix)

1. **Anemic domain models** → Move logic to entities
2. **Primitive obsession** → Use Value Objects
3. **Missing tests** → Add integration tests
4. **Complex methods** → Extract and simplify
5. **Duplicate code** → Extract common patterns

### Best Practices

1. **Prefer composition over inheritance**
2. **Fail fast** - validate early
3. **Make illegal states unrepresentable**
4. **Use Optional instead of null**
5. **Immutability by default**

---

## Architecture Overview

### Modular Architecture by Feature

The codebase follows a modular architecture organized by feature/bounded context:

**Package Structure** (16 modules verified by Spring Modulith):
- `appevents/` - App-facing health events submission API
- `healthevents/` - Core event storage and management (event log, validation)
- `dailysummary/` - Daily aggregated summaries from health events
- `steps/` - Steps tracking projections and queries
- `workout/` - Workout projections and queries
- `workoutimport/` - Workout import from external sources
- `sleep/` - Sleep data projections and queries
- `sleepimport/` - Sleep data import from external sources
- `calories/` - Calories tracking projections
- `activity/` - Activity minutes projections
- `meals/` - Meal tracking projections and queries
- `mealimport/` - Meal import from external sources (with AI-powered drafts)
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

**Optimistic Locking**: All projection entities use `@Version` for concurrency control:
- Each projector wraps save in try-catch for `ObjectOptimisticLockingFailureException`
- On version conflict, single retry is attempted
- Prevents lost updates when multiple events target the same projection row
- See `OptimisticLockingSpec` for integration tests

### AI Health Assistant (Spring AI + Gemini)

**Architecture**: Natural language interface for querying health data using Gemini 3 Flash with SSE streaming.

**Flow**:
1. POST /v1/assistant/chat → `AssistantController` (SSE endpoint)
2. → `AssistantService.streamChat()` builds dynamic system instruction with current date
3. → `ChatClient` (Spring AI) streams to Gemini with function calling enabled
4. → AI decides which tools to call based on user question
5. → `HealthTools` executes tool calls (delegates to facades)
6. → Results streamed back as SSE events: `ContentEvent`, `ToolCallEvent`, `ToolResultEvent`, `DoneEvent`

**Key Features**:
- **Conversation History**: Multi-turn conversations with context retention (last 20 messages)
- **Smart Date Recognition**: AI automatically converts natural language ("today", "last week", "last month") to ISO-8601 dates
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
- System instruction must include current date at the top for date recognition to work
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
export GEMINI_API_KEY="your-api-key"
export GEMINI_MODEL="gemini-3-flash-preview"  # optional, this is default
```

### Health Data Push Model (Health Connect)

**Architecture**: The app uses a PUSH model where health data is submitted directly from Health Connect on the mobile device via `POST /v1/health-events`. The previous Google Fit polling model has been removed.

**Google Fit Module** (Legacy/Optional):
- Manual sync endpoints still available for historical backfill
- `POST /v1/google-fit/sync/day?date=YYYY-MM-DD` - Sync specific day (up to 5 years back)
- Uses virtual threads (Project Loom) for parallel processing
- Automatic reprojection after sync completion

**Key Files**: `GoogleFitFacade.java`, `GoogleFitSyncService.java`

### HMAC Authentication

**Protected Endpoints**: All `/v1/*` endpoints require HMAC signature (including `/v1/daily-summaries`, `/v1/exercises`, `/v1/health-events`, etc.)

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
- `daily_summaries` - Aggregated daily metrics (JSONB summary, ai_summary_cache, device_id)
- `conversations` - AI assistant conversation tracking
- `conversation_messages` - Message history per conversation

**Projection Tables** (all have `version` column for optimistic locking):
- `steps_hourly_projections`, `steps_daily_projections`
- `workout_projections`, `workout_exercise_projections`, `workout_set_projections`
- `sleep_projections`
- `calories_hourly_projections`, `calories_daily_projections`
- `activity_hourly_projections`, `activity_daily_projections`
- `meal_projections`, `meal_daily_projections`
- `meal_import_drafts` (AI-powered meal import)
- `exercise_name_mappings` (maps exercise names to catalog IDs for statistics)

**Migrations**: Flyway versioned in `src/main/resources/db/migration/` (V1-V35)

---

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

---

## Testing

**Test Framework**: Spock (Groovy) with Spring Boot Test + Testcontainers

**Integration Tests** (`integration-tests/` module):
- 420 Spock specifications
- PostgreSQL via Testcontainers
- REST Assured for API testing
- WireMock for mocking external APIs

**Test Spec Files**:
- Event validation: `StepsEventValidationSpec`, `SleepEventValidationSpec`, `WorkoutSpec`, `MealEventSpec`, `HeartRateEventSpec`, `DistanceEventSpec`, `WalkingSessionEventSpec`, `ActiveMinutesEventSpec`, `ActiveCaloriesEventSpec`
- Projections: `StepsProjectionSpec`, `SleepProjectionSpec`, `WorkoutProjectionSpec`, `CaloriesProjectionSpec`, `ActivityProjectionSpec`, `MealProjectionSpec`, `ExerciseStatisticsSpec`
- Features: `DailySummarySpec`, `AssistantSpec`, `ConversationHistorySpec`, `GoogleFitSyncSpec`, `HmacAuthenticationSpec`, `BatchEventIngestionSpec`
- Import: `WorkoutImportSpec`, `MealImportSpec`, `SleepImportSpec`, `MealImportDraftSpec`
- AI Evaluation: `evaluation/AiHallucinationSpec`, `AiDateRecognitionSpec`, `AiToolErrorHandlingSpec`, `AiConversationAccuracySpec`
- Concurrency: `OptimisticLockingSpec`
- AI Features: `AiDailySummarySpec`, `AiDailySummaryCacheSpec`

**Running Tests**:
```bash
# All tests
./gradlew :integration-tests:test

# Specific test class
./gradlew :integration-tests:test --tests "*BatchEventIngestionSpec"

# View report
open integration-tests/build/reports/tests/test/index.html
```

---

## Configuration

### Required Environment Variables
- `DB_URL` - PostgreSQL JDBC URL
- `DB_USER`, `DB_PASSWORD` - Database credentials
- `HMAC_DEVICES_JSON` - JSON map of device secrets (required for HMAC endpoints)

### Optional Environment Variables
- `HMAC_TOLERANCE_SEC` - Timestamp tolerance (default: 600)
- `NONCE_CACHE_TTL_SEC` - Nonce cache TTL (default: 600)
- `GEMINI_API_KEY` - Gemini API key for AI Assistant (required for /v1/assistant/chat)
- `GEMINI_MODEL` - Gemini model name (default: gemini-3-flash-preview)
- `GOOGLE_FIT_CLIENT_ID` - Google OAuth client ID (for historical sync)
- `GOOGLE_FIT_CLIENT_SECRET` - Google OAuth client secret (for historical sync)
- `GOOGLE_FIT_REFRESH_TOKEN` - Google OAuth refresh token (for historical sync)

### Spring Features
- `@EnableScheduling` - Google Fit sync scheduled tasks
- `@EnableCaching` - Caffeine cache for nonce replay protection
- `@EnableFeignClients` - Google Fit API client
- `@EnableJpaRepositories` - Spring Data JPA
- Spring AI with Google Gemini - AI assistant with function calling
- Spring Modulith for modular architecture verification

### Code Quality Tools
- **SpotBugs**: Static analysis (`./gradlew spotbugsMain`, config: `config/spotbugs/exclude.xml`)
- **PMD**: Code style checking (`./gradlew pmdMain`, config: `config/pmd/ruleset.xml`)
- **JaCoCo**: Code coverage (`./gradlew jacocoTestReport`, reports in `build/reports/jacoco/`)
- **Modularity Tests**: `ModularityTests.java` verifies module boundaries (16 modules)

---

## API Endpoints

### AI Assistant
- `POST /v1/assistant/chat` - Chat with AI assistant (SSE streaming, HMAC auth required)
  - Request: `{"message": "How many steps did I take today?"}`
  - Response: SSE stream with `ContentEvent`, `ToolCallEvent`, `ToolResultEvent`, `DoneEvent`

### Event Ingestion
- `POST /v1/health-events` - Batch event ingestion (HMAC auth required)

### Daily Summaries
- `GET /v1/daily-summaries/{date}` - Get daily summary (HMAC auth required)
- `GET /v1/daily-summaries/range?from={date}&to={date}` - Get daily summary range (HMAC auth required)

### Google Fit Sync (Historical/Legacy)
- `POST /v1/google-fit/sync/day?date=YYYY-MM-DD` - Sync specific day (up to 5 years back)

### Import APIs
- `POST /v1/sleep/import-image?year=YYYY` - Import sleep from screenshot (year optional, default: current year)
- `POST /v1/workouts/import-image` - Import workout from screenshot
- `POST /v1/meals/import-image` - Import meal from image (returns draft for confirmation)

### Query APIs
- `GET /v1/steps/daily/{date}` - Daily step breakdown
- `GET /v1/steps/daily/range?from={date}&to={date}` - Step range summary
- `GET /v1/workouts/{workoutId}` - Workout details
- `GET /v1/workouts?from={date}&to={date}` - Workouts by date range
- `GET /v1/sleep/range?from={date}&to={date}` - Sleep data range
- `GET /v1/meals/range?from={date}&to={date}` - Meals data range
- `GET /v1/exercises/{exerciseId}/statistics?from={date}&to={date}` - Exercise statistics with progression analysis (HMAC auth required)

### Monitoring
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Prometheus metrics
- `GET /swagger-ui.html` - OpenAPI documentation

---

## Key Design Principles

1. **Events are source of truth** - All data in immutable event log
2. **Projections are regenerable** - Can be rebuilt from events
3. **Idempotency guaranteed** - Safe to retry all operations
4. **No blocking failures** - Projection errors don't block ingestion
5. **Transactional operations** - `@Transactional` ensures atomicity
6. **Type safety** - Value objects, enums, validation at boundaries
7. **Time zone consistency** - UTC internally, Poland time for daily boundaries

---

## Common Development Patterns

### Adding a New Event Type

1. Define payload DTO in `dto/payload/` (implement `EventPayload`)
2. Add validation logic in `EventValidator.validatePayload()`
3. Add event type to `EventType` enum
4. Update `DailySummaryAggregator` if it affects daily summaries
5. Create projector if needed (e.g., `StepsProjector`, `WorkoutProjector`)
6. Add integration tests

### Adding a New Projection

1. Create Flyway migration for projection tables (include `version` column!)
2. Create projector class (e.g., `StepsProjector`)
3. Wire into `StoreHealthEventsCommandHandler.projectEvents()`
4. Create JPA entities and repositories (with `@Version`, no `@Setter`)
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

---

## Tech Stack

- **Java 21** with virtual threads (Project Loom)
- **Spring Boot 3.3.5** (Web, Data JPA, Actuator, Validation)
- **Spring AI 1.1.0** with Google Gemini 3 Flash integration
- **Spring Modulith 1.3.1** for modular architecture
- **PostgreSQL 16** with JSONB
- **Gradle 8.5+** with Kotlin DSL
- **Flyway** for database migrations
- **Caffeine** for in-memory caching
- **Feign** for Google Fit API client
- **MapStruct** for object mapping
- **Testcontainers** for integration testing
- **Spock** (Groovy) for test specifications
- **Docker & Docker Compose** for containerization
- **SpotBugs + PMD + JaCoCo** for code quality
