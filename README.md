# Health Assistant Server

A Spring Boot 3.3 backend service for health data ingestion, synchronization with Google Fit, and daily health summaries with HMAC authentication and idempotency guarantees

## 🚀 Features

### 🤖 AI Health Assistant (NEW!)
- **Natural Language Interface**: Ask questions in Polish about your health data
- **Conversation History**: Multi-turn conversations with context retention (last 20 messages)
- **Smart Date Recognition**: Understands "dzisiaj", "wczoraj", "ostatni tydzień", "ostatni miesiąc"
- **Real-time Streaming**: SSE-based responses that stream word-by-word
- **Intelligent Tool Selection**: AI automatically chooses which data to fetch
- **5 Health Tools**: Sleep data, steps, workouts, meals, and daily summaries
- **Gemini 2.0 Flash**: Fast and accurate responses powered by Google's latest model

### Core Event Ingestion
- **Batch Event Ingestion**: Accept up to 100 events per request
- **HMAC Authentication**: Secure header-based authentication with replay protection
- **Idempotency**: Automatic deduplication using client-provided keys
- **Append-Only Storage**: Events stored in PostgreSQL with JSONB payloads
- **Event Validation**: Type-specific payload validation

### Health Connect Integration (Push Model)
- **Event-Based Ingestion**: Health data pushed directly from mobile app via Health Connect
- **Historical Data Sync**: Optional Google Fit sync for backfilling historical data
- **15-Minute Buckets**: Steps, distance, calories, and heart rate in 15-minute intervals
- **Session Import**: Sleep sessions and walking sessions with step attribution
- **Activity Time Calculation**: Automatic detection of active periods from step data

### Daily Summaries
- **Automated Aggregation**: Daily health metrics calculated from events
- **Summary API**: Retrieve daily summaries with steps, calories, sleep, and activity time
- **Walking Sessions**: Track individual walks with duration, steps, distance, and calories

### Infrastructure
- **PostgreSQL 16** with JSONB support
- **Flyway** database migrations
- **Docker** support with docker-compose
- **Health Checks** and Prometheus metrics
- **OpenAPI Documentation** via Swagger UI

## 📋 Tech Stack

- **Java 21** (Eclipse Temurin)
- **Spring Boot 3.3.5**
- **PostgreSQL 16** with JSONB support
- **Gradle (Kotlin DSL)**
- **Flyway** for database migrations
- **Caffeine** for nonce caching (anti-replay)
- **Virtual Threads** for parallel processing
- **Testcontainers** for integration testing
- **Docker & Docker Compose** for containerization

## 🏗️ Project Structure

```
health-assistant-server/
├── src/main/java/com/healthassistant/
│   ├── application/
│   │   ├── ingestion/          # Event storage and processing
│   │   ├── summary/            # Daily summary aggregation
│   │   └── sync/               # Google Fit synchronization
│   ├── domain/
│   │   ├── event/              # Event domain model
│   │   └── summary/            # Summary domain model
│   ├── infrastructure/
│   │   ├── googlefit/          # Google Fit API client
│   │   └── web/                # REST controllers, security
│   ├── config/                 # Configuration classes
│   └── dto/                    # Data transfer objects
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/           # Flyway migrations
├── integration-tests/          # Integration tests (366 Spock tests)
├── docker-compose.yml
├── Dockerfile
└── README.md
```

## 🔧 Configuration

Configure via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/health_assistant` |
| `DB_USER` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `HMAC_DEVICES_JSON` | JSON map of device ID to base64 secret | `{"test-device-1":"dGVzdC1zZWNyZXQtMTIz"}` |
| `HMAC_TOLERANCE_SEC` | Timestamp tolerance in seconds | `600` |
| `NONCE_CACHE_TTL_SEC` | Nonce cache TTL (anti-replay) | `600` |
| `GOOGLE_FIT_CLIENT_ID` | Google OAuth client ID | - |
| `GOOGLE_FIT_CLIENT_SECRET` | Google OAuth client secret | - |
| `GOOGLE_FIT_REFRESH_TOKEN` | Google OAuth refresh token | - |
| `GEMINI_API_KEY` | Google Gemini API key for AI Assistant | - |
| `GEMINI_MODEL` | Gemini model name | `gemini-3-flash-preview` |

## 🚀 Quick Start

### Using Docker Compose (Recommended)

1. **Start services**:
   ```bash
   docker-compose up --build
   ```

2. **Access the API**:
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

## 📡 API Endpoints

### AI Assistant
- `POST /v1/assistant/chat` - Chat with AI health assistant (SSE streaming, HMAC auth required)
  - **Request**: `{"message": "Ile kroków zrobiłem dzisiaj?"}`
  - **Response**: Server-Sent Events stream with real-time answers

### Event Ingestion
- `POST /v1/health-events` - Batch event ingestion (HMAC auth required)

### Google Fit Sync (Historical/Legacy)
- `POST /v1/google-fit/sync/history?days=7` - Sync historical data from Google Fit

### Daily Summaries
- `GET /v1/daily-summaries/{date}` - Get daily summary (HMAC auth required)
- `GET /v1/daily-summaries/range?from={date}&to={date}` - Get daily summary range

### Query APIs
- `GET /v1/steps/daily/{date}` - Daily step breakdown
- `GET /v1/steps/daily/range?from={date}&to={date}` - Step range summary
- `GET /v1/workouts/{workoutId}` - Workout details
- `GET /v1/workouts/date/{date}` - Workouts on date
- `GET /v1/sleep/range?from={date}&to={date}` - Sleep data range
- `GET /v1/meals/range?from={date}&to={date}` - Meals data range

See [Swagger UI](http://localhost:8080/swagger-ui.html) for detailed documentation.

## 📊 Supported Event Types

| Event Type | Description | Status |
|------------|-------------|--------|
| `StepsBucketedRecorded.v1` | Bucketed step counts (1-minute intervals) | ✅ Active |
| `HeartRateSummaryRecorded.v1` | Heart rate statistics over a time window | ✅ Active |
| `SleepSessionRecorded.v1` | Sleep session with optional stage breakdown | ✅ Active |
| `ActiveCaloriesBurnedRecorded.v1` | Active calories burned in a time bucket | ✅ Active |
| `ActiveMinutesRecorded.v1` | Active minutes in a time bucket | ✅ Active |
| `DistanceBucketedRecorded.v1` | Distance traveled in bucketed intervals | ✅ Active |
| `WalkingSessionRecorded.v1` | Walking session with steps, distance, calories | ✅ Active |
| `WorkoutRecorded.v1` | Strength workout with exercises, sets, and reps | ✅ Active |
| `MealRecorded.v1` | Meal with nutrition info and health rating | ✅ Active |

## 🗄️ Database Schema

### Tables

**`health_events`** - Append-only event storage
- `id` (BIGSERIAL): Primary key
- `event_id` (VARCHAR): Server-generated unique ID
- `idempotency_key` (VARCHAR): Client-provided deduplication key (non-unique)
- `event_type` (VARCHAR): Event type
- `occurred_at` (TIMESTAMPTZ): Event occurrence time
- `payload` (JSONB): Event-specific data
- `device_id` (VARCHAR): Source device
- `created_at` (TIMESTAMPTZ): Server ingestion time

**`daily_summaries`** - Aggregated daily metrics
- `id` (BIGSERIAL): Primary key
- `date` (DATE): Summary date (unique)
- `total_steps` (INTEGER): Total steps for the day
- `total_active_calories` (DOUBLE): Total active calories burned
- `total_distance_meters` (DOUBLE): Total distance in meters
- `sleep_duration_minutes` (INTEGER): Total sleep duration
- `activity_time_minutes` (INTEGER): Time spent active (calculated from steps)
- `updated_at` (TIMESTAMPTZ): Last update time

## 🔄 Health Data Synchronization

### Push Model (Health Connect)
Health data is pushed directly from the mobile app via Health Connect:
- Events submitted via `POST /v1/health-events`
- Idempotent processing with automatic deduplication
- Real-time projection updates

### Historical Google Fit Sync (Optional)
For backfilling historical data from Google Fit:
```bash
curl -X POST http://localhost:8080/v1/google-fit/sync/history?days=30
```
- Processes each day in parallel using virtual threads
- Safe for large date ranges (1-365 days)
- Idempotent - can be run multiple times
- Automatic reprojection after sync completion

### Activity Detection
- Automatically calculates activity time from step patterns
- Identifies walking sessions
- Attributes steps to walks based on time overlap

## 🧪 Testing

```bash
# Run all tests (main + integration)
./gradlew build

# Run only integration tests
./gradlew :integration-tests:test

# View test reports
open integration-tests/build/reports/tests/test/index.html
```

**Test Coverage**: 366 integration tests covering:
- Event validation (Steps, Sleep, Workout, Meal, Heart Rate, Distance, Walking Session, Active Minutes, Active Calories)
- Projections (Steps, Sleep, Workout, Calories, Activity, Meals)
- Features (Daily Summaries, AI Assistant, Conversation History, Google Fit Sync)
- Import (Workout Import, Meal Import, Sleep Import)
- AI Evaluation (LLM-as-a-Judge hallucination tests)
- Concurrency (Optimistic Locking)

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

## 🔒 Security

1. **HMAC Authentication**:
   - HMAC-SHA256 signing with device secrets
   - Timestamp validation (prevents time-based attacks)
   - Nonce tracking (prevents replay attacks)

2. **Input Validation**:
   - Bean Validation (@Valid annotations)
   - Type-specific payload validation
   - Batch size limits

3. **Database Security**:
   - Prepared statements (SQL injection prevention)
   - Connection pooling with HikariCP

## 🚧 Recent Updates

- ✅ **Optimistic Locking** - @Version-based concurrency control for all projections with automatic retry
- ✅ **Simplified Google Fit Sync** - Streamlined historical sync with automatic reprojection
- ✅ **Comprehensive Test Coverage** - 366 integration tests covering all 9 event types with validation
- ✅ **Conversation History** - Multi-turn AI conversations with context retention
- ✅ **AI Health Assistant** - Natural language chat interface with Gemini 2.0 Flash
- ✅ **Smart Date Recognition** - Automatic interpretation of "dzisiaj", "ostatni tydzień", etc.
- ✅ **Real-time SSE Streaming** - Word-by-word AI responses via Server-Sent Events
- ✅ **Meal Tracking** - MealRecorded events with nutrition and health ratings
- ✅ **Workout Projections** - Pre-calculated workout metrics and volumes
- ✅ Historical sync with parallel processing
- ✅ Walking session tracking with step attribution
- ✅ Activity time calculation from step patterns

## 📝 License

Copyright © 2025. All rights reserved.

---

**Built with ❤️ using Spring Boot 3.3 and Java 21**