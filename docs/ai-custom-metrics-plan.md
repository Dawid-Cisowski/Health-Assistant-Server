# AI Custom Metrics Plan

## Current State

- Micrometer + Prometheus + Stackdriver already configured (`OtlpMetricsConfiguration.java`)
- Common tags (application, version, environment) applied to all metrics
- Spring AI observations enabled (without prompt/completion content)
- Token usage captured in code but **not exposed to Prometheus**
- **Zero custom metrics** for any AI feature

---

## Metrics by Feature

### 1. AI Chat Assistant (`assistant/`)

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `ai.chat.requests` | Counter | `status` (success/error/rate_limited) | Volume + error rate |
| `ai.chat.duration` | Timer | `status` | End-to-end latency |
| `ai.chat.tokens.prompt` | DistributionSummary | — | Prompt token distribution |
| `ai.chat.tokens.completion` | DistributionSummary | — | Completion token distribution |
| `ai.chat.tokens.total` | Counter | `type` (prompt/completion) | Running total tokens (cost) |
| `ai.chat.tool_calls` | Counter | `tool` (getStepsData, getSleepData, etc.) | Tool usage frequency |
| `ai.chat.tool_calls.duration` | Timer | `tool`, `status` | Tool execution latency |
| `ai.chat.conversations.active` | Gauge | — | Active conversations (last 24h) |
| `ai.chat.rate_limit.rejected` | Counter | — | Rate limit rejections |

**Rationale**: Token usage = direct cost. Latency = user experience. Tool distribution = what users actually ask about. Rate limit rejections = capacity planning.

### 2. AI Daily Summary & Health Reports (`dailysummary/`)

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `ai.summary.requests` | Counter | `type` (daily_summary/daily_report/range_report), `status`, `cache` (hit/miss) | Volume + cache effectiveness |
| `ai.summary.duration` | Timer | `type` | Generation latency |
| `ai.summary.tokens.prompt` | DistributionSummary | `type` | Token usage per generation |
| `ai.summary.tokens.completion` | DistributionSummary | `type` | Token usage per generation |
| `ai.summary.tokens.total` | Counter | `type`, `token_type` | Running total tokens (cost) |
| `ai.summary.input.truncated` | Counter | — | Input exceeds 50k char limit |

**Rationale**: Cache hit ratio = cost savings indicator. If low, caching strategy needs tuning. Truncation = data growth signal.

### 3. AI Image Imports (`mealimport/`, `workoutimport/`, `sleepimport/`, `weightimport/`)

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `ai.import.requests` | Counter | `import_type` (meal/workout/sleep/weight), `status` (success/error/not_meal), `mode` (direct/draft) | Volume + success rate |
| `ai.import.duration` | Timer | `import_type` | AI extraction latency |
| `ai.import.tokens.prompt` | DistributionSummary | `import_type` | Token usage (multimodal = expensive) |
| `ai.import.tokens.completion` | DistributionSummary | `import_type` | Token usage |
| `ai.import.tokens.total` | Counter | `import_type`, `token_type` | Running total (cost) |
| `ai.import.confidence` | DistributionSummary | `import_type` | AI confidence distribution |
| `ai.import.images_per_request` | DistributionSummary | `import_type` | Images per request |
| `ai.import.draft.confirmed` | Counter | `action` (confirmed/expired/abandoned) | Draft conversion funnel |
| `ai.import.reanalysis` | Counter | `status` | Re-analysis frequency |

**Rationale**: Image imports are the most expensive AI calls (multimodal). Confidence distribution shows AI reliability. Draft funnel shows UX effectiveness.

### 4. Guardrails (`guardrails/`)

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `ai.guardrail.checks` | Counter | `profile` (CHAT/IMAGE_IMPORT/DATA_EXTRACTION), `result` (passed/blocked/sanitized) | Guardrail activity |
| `ai.guardrail.injection.detected` | Counter | `profile`, `pattern_type` (text/json) | Injection detection frequency |
| `ai.guardrail.validation.failed` | Counter | `profile`, `reason` (too_long/blank/null) | Input validation failures |

**Rationale**: Security observability. Injection spike = someone probing. Validation failures = client-side validation gap.

### 5. Cross-cutting

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `ai.tokens.cost.total` | Counter | `feature` (chat/summary/report/import), `token_type` | Global token consumption for cost dashboard |
| `ai.errors` | Counter | `feature`, `error_type` (api_error/timeout/parse_error/empty_response) | Centralized error tracking |

---

## Implementation Plan

### Architecture: Central `AiMetricsRecorder`

Single service wrapping all Micrometer calls. AI services inject `AiMetricsRecorder` instead of `MeterRegistry` directly.

Location: `com.healthassistant.config.AiMetricsRecorder`

```java
@Component
@RequiredArgsConstructor
public class AiMetricsRecorder {

    private final MeterRegistry registry;

    // --- Chat ---
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordChatRequest(Timer.Sample sample, String status) {
        sample.stop(Timer.builder("ai.chat.duration")
            .tag("status", status)
            .register(registry));
        registry.counter("ai.chat.requests", "status", status).increment();
    }

    public void recordChatTokens(Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            registry.summary("ai.chat.tokens.prompt").record(promptTokens);
            registry.counter("ai.tokens.cost.total", "feature", "chat",
                "token_type", "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            registry.summary("ai.chat.tokens.completion").record(completionTokens);
            registry.counter("ai.tokens.cost.total", "feature", "chat",
                "token_type", "completion").increment(completionTokens);
        }
    }

    public void recordToolCall(String toolName, Timer.Sample sample, String status) {
        sample.stop(Timer.builder("ai.chat.tool_calls.duration")
            .tag("tool", toolName).tag("status", status)
            .register(registry));
        registry.counter("ai.chat.tool_calls", "tool", toolName).increment();
    }

    public void recordRateLimitRejected() {
        registry.counter("ai.chat.rate_limit.rejected").increment();
    }

    // --- Summary/Report ---
    public void recordSummaryRequest(String type, Timer.Sample sample,
                                     String status, boolean cacheHit) {
        sample.stop(Timer.builder("ai.summary.duration")
            .tag("type", type).register(registry));
        registry.counter("ai.summary.requests",
            "type", type, "status", status,
            "cache", cacheHit ? "hit" : "miss").increment();
    }

    public void recordSummaryTokens(String type, Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            registry.summary("ai.summary.tokens.prompt", "type", type).record(promptTokens);
            registry.counter("ai.tokens.cost.total", "feature", type,
                "token_type", "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            registry.summary("ai.summary.tokens.completion", "type", type).record(completionTokens);
            registry.counter("ai.tokens.cost.total", "feature", type,
                "token_type", "completion").increment(completionTokens);
        }
    }

    public void recordSummaryInputTruncated() {
        registry.counter("ai.summary.input.truncated").increment();
    }

    // --- Import ---
    public void recordImportRequest(String importType, Timer.Sample sample,
                                    String status, String mode) {
        sample.stop(Timer.builder("ai.import.duration")
            .tag("import_type", importType).register(registry));
        registry.counter("ai.import.requests",
            "import_type", importType, "status", status,
            "mode", mode).increment();
    }

    public void recordImportTokens(String importType, Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            registry.summary("ai.import.tokens.prompt", "import_type", importType).record(promptTokens);
            registry.counter("ai.tokens.cost.total", "feature", "import_" + importType,
                "token_type", "prompt").increment(promptTokens);
        }
        if (completionTokens != null) {
            registry.summary("ai.import.tokens.completion", "import_type", importType).record(completionTokens);
            registry.counter("ai.tokens.cost.total", "feature", "import_" + importType,
                "token_type", "completion").increment(completionTokens);
        }
    }

    public void recordImportConfidence(String importType, double confidence) {
        registry.summary("ai.import.confidence", "import_type", importType).record(confidence);
    }

    public void recordImportImageCount(String importType, int imageCount) {
        registry.summary("ai.import.images_per_request", "import_type", importType).record(imageCount);
    }

    public void recordDraftAction(String action) {
        registry.counter("ai.import.draft.confirmed", "action", action).increment();
    }

    public void recordReanalysis(String status) {
        registry.counter("ai.import.reanalysis", "status", status).increment();
    }

    // --- Guardrails ---
    public void recordGuardrailCheck(String profile, String result) {
        registry.counter("ai.guardrail.checks", "profile", profile, "result", result).increment();
    }

    public void recordInjectionDetected(String profile, String patternType) {
        registry.counter("ai.guardrail.injection.detected",
            "profile", profile, "pattern_type", patternType).increment();
    }

    public void recordGuardrailValidationFailed(String profile, String reason) {
        registry.counter("ai.guardrail.validation.failed",
            "profile", profile, "reason", reason).increment();
    }

    // --- Errors ---
    public void recordAiError(String feature, String errorType) {
        registry.counter("ai.errors", "feature", feature, "error_type", errorType).increment();
    }
}
```

### Integration Points

| File | Instrumentation |
|---|---|
| `AssistantService.streamChat()` | `startTimer()` at entry, `recordChatRequest()` in doFinally, `recordChatTokens()` after response |
| `AssistantController.chat()` | `recordRateLimitRejected()` when rate limit triggered |
| `HealthTools` (each `@Tool` method) | `startTimer()` + `recordToolCall()` wrapping each tool execution |
| `AiDailySummaryService.generateSummary()` | Timer + cache hit/miss counter + token recording |
| `AiHealthReportService.generateDailyReport()` | Timer + cache hit/miss + tokens |
| `AiHealthReportService.generateRangeReport()` | Timer + tokens |
| `AiHealthReportService.callAi()` | `recordSummaryInputTruncated()` when input > 50k |
| `MealContentExtractor.extract()` | Timer + tokens + confidence + image count |
| `WorkoutImageExtractor.extract()` | Timer + tokens + confidence |
| `SleepImageExtractor.extract()` | Timer + tokens + confidence |
| `WeightImageExtractor.extract()` | Timer + tokens + confidence |
| `MealImportService` (confirm/expire draft) | `recordDraftAction()` |
| `MealContentExtractor.reAnalyzeWithContext()` | `recordReanalysis()` |
| `GuardrailChain.evaluate()` | `recordGuardrailCheck()` with pass/block/sanitize result |
| `PromptInjectionGuardrail.check()` | `recordInjectionDetected()` on match |
| `InputValidationGuardrail.check()` | `recordGuardrailValidationFailed()` on failure |

### Implementation Phases

#### Phase 1: Core Cost Visibility (highest ROI)
1. Create `AiMetricsRecorder` in `config/`
2. Add token counters to all AI call sites (chat, summary, report, imports)
3. Add request counters with status tags
4. Add `ai.tokens.cost.total` aggregate counter

**Files to modify**: `AssistantService`, `AiDailySummaryService`, `AiHealthReportService`, `MealContentExtractor`, `WorkoutImageExtractor`, `SleepImageExtractor`, `WeightImageExtractor`

#### Phase 2: Latency & Performance
4. Add timers on chat, summary, report, import operations
5. Add tool call counters + timers in `HealthTools`
6. Add cache hit/miss counters for summaries and reports

**Files to modify**: All Phase 1 files + `HealthTools`, `AssistantController`

#### Phase 3: Quality & Security
7. Add confidence score distributions for imports
8. Add guardrail check counters + injection detection
9. Add draft conversion funnel metrics
10. Add error type breakdown counters

**Files to modify**: All import services + `GuardrailChain`, `PromptInjectionGuardrail`, `InputValidationGuardrail`, `MealImportService`

---

## Grafana Dashboard Structure

```
AI Overview:
  - Total AI requests/min (all features)
  - Total tokens/hour (cost proxy)
  - Error rate %

Chat:
  - Requests/min by status
  - p50/p95/p99 latency
  - Tool call distribution (pie chart)
  - Token usage (prompt vs completion)

Reports:
  - Summary/Report requests/min
  - Cache hit ratio (%)
  - Generation latency p50/p95

Imports:
  - Imports/min by type
  - Success rate by type
  - Confidence score histogram
  - Draft conversion funnel (meal)

Security:
  - Guardrail checks/min
  - Injection attempts
  - Rate limit rejections
```

## Suggested Alerts

| Alert | Condition | Severity |
|---|---|---|
| Token burn rate high | `rate(ai.tokens.cost.total[1h])` > threshold | Warning |
| Chat error rate | error / total > 5% over 5min | Critical |
| Chat p95 latency | > 10s | Warning |
| Import failure rate | error / total > 20% over 15min | Warning |
| Prompt injection spike | `rate(ai.guardrail.injection.detected[5m])` > 5 | Critical |
| Cache hit ratio drop | hit / total < 50% over 1h | Warning |
| AI empty response | `ai.errors{error_type=empty_response}` > 3 in 5min | Warning |

---

## Naming Convention

All metrics follow `ai.<feature>.<metric>` pattern:
- `ai.chat.*` — Chat assistant
- `ai.summary.*` — Daily summaries and health reports
- `ai.import.*` — Image imports
- `ai.guardrail.*` — Security guardrails
- `ai.tokens.cost.total` — Cross-cutting cost aggregate
- `ai.errors` — Cross-cutting error aggregate

Tags use lowercase snake_case. Values are lowercase.

## Cardinality Budget

Estimated unique time series:
- Chat: ~30 (3 statuses x counters + timers + 8 tools x 2 tags)
- Summary: ~25 (3 types x 3 metrics x 2 cache states)
- Import: ~40 (4 types x counters + timers + confidence + drafts)
- Guardrails: ~15 (3 profiles x 3 results + injection patterns)
- Cross-cutting: ~20

**Total: ~130 unique time series** — well within Prometheus/Stackdriver limits.
