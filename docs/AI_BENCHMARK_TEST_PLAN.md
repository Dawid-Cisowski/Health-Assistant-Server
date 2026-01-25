# AI Benchmark Test Plan - Happy Path

## Overview

This document outlines the plan for creating comprehensive benchmark tests for the AI Health Assistant using Spring AI. The benchmarks will measure:

1. **Quality** - Response accuracy, correctness, and consistency
2. **Cost** - Token usage (input/output/total)
3. **Time** - Latency and response times

## Architecture

### Technology Stack

- **Spring AI 1.1.0** - ChatClient with Google Gemini integration
- **Spock Framework** - Test specifications (Groovy)
- **Testcontainers** - PostgreSQL isolation
- **Micrometer** - Metrics collection (already configured)
- **LLM-as-a-Judge** - Quality evaluation pattern (existing)

### Key Components

```
integration-tests/src/test/
├── groovy/com/healthassistant/benchmark/
│   ├── BaseBenchmarkSpec.groovy          # Base class with metrics collection
│   ├── BenchmarkResult.groovy            # Result data class
│   ├── BenchmarkReporter.groovy          # Report generation
│   │
│   ├── quality/                          # Quality benchmarks
│   │   ├── HappyPathQualitySpec.groovy   # Happy path quality tests
│   │   └── QualityMetrics.groovy         # Quality score calculations
│   │
│   ├── cost/                             # Cost benchmarks
│   │   ├── TokenUsageSpec.groovy         # Token usage tracking
│   │   └── CostCalculator.groovy         # Cost estimation
│   │
│   └── latency/                          # Latency benchmarks
│       ├── ResponseTimeSpec.groovy       # Response time measurements
│       └── LatencyMetrics.groovy         # Latency statistics
│
└── java/com/healthassistant/benchmark/
    ├── BenchmarkMetricsCollector.java    # Spring AI metrics extraction
    └── TokenUsageExtractor.java          # ChatResponse token extraction
```

---

## 1. Quality Benchmarks

### 1.1 Metrics to Measure

| Metric | Description | Target |
|--------|-------------|--------|
| **Accuracy Rate** | % of correct responses (LLM-judge verified) | ≥95% |
| **Hallucination Rate** | % of fabricated data responses | ≤2% |
| **Tool Call Success** | % of correct tool selections | ≥98% |
| **Data Completeness** | % of responses with all requested data | ≥90% |
| **Language Consistency** | % matching user's language | 100% |

### 1.2 Happy Path Test Scenarios

#### A. Single Metric Queries (10 scenarios)

```groovy
// Scenario: Steps query for today
def "HP-Q-001: Returns correct step count for today"() {
    given: "8500 steps recorded today"
    submitStepsForToday(8500)
    waitForProjections()

    when: "asking about today's steps"
    def result = measureAndAsk("How many steps did I take today?")

    then: "response contains correct data"
    def evaluation = qualityEvaluator.evaluate(
        claim: "User took 8500 steps today",
        response: result.response
    )
    evaluation.isPass()

    and: "record metrics"
    recordBenchmark("HP-Q-001", result, evaluation)
}
```

**Full scenario list:**
1. `HP-Q-001`: Steps query - single day
2. `HP-Q-002`: Sleep query - last night
3. `HP-Q-003`: Calories burned - today
4. `HP-Q-004`: Workout query - today
5. `HP-Q-005`: Meals query - today
6. `HP-Q-006`: Daily summary - complete data
7. `HP-Q-007`: Weight query - current
8. `HP-Q-008`: Heart rate query - today
9. `HP-Q-009`: Activity minutes - today
10. `HP-Q-010`: Distance query - today

#### B. Date Range Queries (5 scenarios)

```groovy
// Scenario: Weekly steps summary
def "HP-Q-011: Returns correct weekly steps summary"() {
    given: "steps recorded for 7 days"
    (0..6).each { daysAgo ->
        submitStepsForDate(today.minusDays(daysAgo), 5000 + (daysAgo * 1000))
    }
    waitForProjections()

    when: "asking about this week"
    def result = measureAndAsk("How many steps did I take this week?")

    then: "response contains correct total (38000 steps)"
    def evaluation = qualityEvaluator.evaluate(
        claim: "User took approximately 38000 total steps this week",
        response: result.response
    )
    evaluation.isPass()
}
```

**Full scenario list:**
11. `HP-Q-011`: Weekly steps summary
12. `HP-Q-012`: Weekly sleep average
13. `HP-Q-013`: Monthly calories total
14. `HP-Q-014`: Weekly workout summary
15. `HP-Q-015`: Monthly meal statistics

#### C. Multi-Tool Queries (5 scenarios)

```groovy
// Scenario: Compare metrics
def "HP-Q-016: Correctly compares steps vs calories"() {
    given: "steps and calories recorded"
    submitStepsForToday(10000)
    submitActiveCalories(450)
    waitForProjections()

    when: "asking comparison question"
    def result = measureAndAsk("What are my steps and calories for today?")

    then: "response contains both metrics correctly"
    def evaluation = qualityEvaluator.evaluate(
        claim: "User has 10000 steps AND 450 active calories today - both numbers mentioned separately",
        response: result.response
    )
    evaluation.isPass()
}
```

**Full scenario list:**
16. `HP-Q-016`: Steps vs calories comparison
17. `HP-Q-017`: Sleep + activity correlation
18. `HP-Q-018`: Workout + nutrition summary
19. `HP-Q-019`: Full daily health overview
20. `HP-Q-020`: Week trend analysis

#### D. Natural Language Understanding (5 scenarios)

```groovy
// Scenario: Polish language query
def "HP-Q-021: Handles Polish language correctly"() {
    given: "steps recorded"
    submitStepsForToday(6000)
    waitForProjections()

    when: "asking in Polish"
    def result = measureAndAsk("Ile kroków zrobiłem dzisiaj?")

    then: "response is in Polish and correct"
    def evaluation = qualityEvaluator.evaluate(
        claim: "User took 6000 steps today, response should be in Polish",
        response: result.response
    )
    evaluation.isPass()
}
```

**Full scenario list:**
21. `HP-Q-021`: Polish language query
22. `HP-Q-022`: "Yesterday" date recognition
23. `HP-Q-023`: "Last week" date recognition
24. `HP-Q-024`: "3 days ago" date recognition
25. `HP-Q-025`: "This month" date recognition

---

## 2. Cost Benchmarks

### 2.1 Metrics to Measure

| Metric | Description | Tracking Method |
|--------|-------------|-----------------|
| **Input Tokens** | Tokens in prompt (system + user + history) | ChatResponse metadata |
| **Output Tokens** | Tokens in response | ChatResponse metadata |
| **Total Tokens** | Input + Output | Calculated |
| **Estimated Cost** | USD based on model pricing | Calculated |

### 2.2 Token Usage Extraction

Spring AI's `ChatResponse` provides token usage metadata:

```java
@Component
public class TokenUsageExtractor {

    public TokenUsage extractFromResponse(ChatResponse response) {
        var metadata = response.getMetadata();
        var usage = metadata.getUsage();

        return new TokenUsage(
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens()
        );
    }
}
```

### 2.3 Cost Calculation

```java
public record CostEstimate(
    long inputTokens,
    long outputTokens,
    double inputCostUsd,
    double outputCostUsd,
    double totalCostUsd
) {
    // Gemini 1.5 Flash pricing (as of 2024)
    private static final double INPUT_COST_PER_1M = 0.075;  // $0.075 per 1M input tokens
    private static final double OUTPUT_COST_PER_1M = 0.30;  // $0.30 per 1M output tokens

    public static CostEstimate calculate(long inputTokens, long outputTokens) {
        double inputCost = (inputTokens / 1_000_000.0) * INPUT_COST_PER_1M;
        double outputCost = (outputTokens / 1_000_000.0) * OUTPUT_COST_PER_1M;
        return new CostEstimate(inputTokens, outputTokens, inputCost, outputCost, inputCost + outputCost);
    }
}
```

### 2.4 Cost Benchmark Scenarios

```groovy
class TokenUsageSpec extends BaseBenchmarkSpec {

    def "COST-001: Single metric query token usage"() {
        given: "simple health data"
        submitStepsForToday(5000)
        waitForProjections()

        when: "asking simple question"
        def result = measureAndAsk("How many steps today?")

        then: "token usage is within expected range"
        result.tokenUsage.inputTokens < 2000      // System prompt + tools
        result.tokenUsage.outputTokens < 500       // Short response
        result.estimatedCostUsd < 0.001            // Under $0.001

        and: "record for reporting"
        recordCostBenchmark("COST-001", result)
    }

    def "COST-002: Complex query with history token usage"() {
        given: "conversation history exists"
        askAssistant("How many steps today?")
        askAssistant("What about yesterday?")

        when: "asking follow-up question"
        def result = measureAndAsk("Compare both days")

        then: "token usage accounts for history"
        result.tokenUsage.inputTokens > 2000      // Includes history
        result.tokenUsage.inputTokens < 5000      // But still bounded

        and: "cost is reasonable"
        result.estimatedCostUsd < 0.005
    }
}
```

**Cost scenario list:**
1. `COST-001`: Single metric query
2. `COST-002`: Query with conversation history (5 messages)
3. `COST-003`: Query with conversation history (10 messages)
4. `COST-004`: Multi-tool query (2 tools)
5. `COST-005`: Multi-tool query (5 tools)
6. `COST-006`: Date range query (7 days)
7. `COST-007`: Date range query (30 days)
8. `COST-008`: Complex analysis query
9. `COST-009`: Polish language query (character count)
10. `COST-010`: Average across all happy path queries

---

## 3. Latency Benchmarks

### 3.1 Metrics to Measure

| Metric | Description | Target |
|--------|-------------|--------|
| **Time to First Token (TTFT)** | Latency until first response chunk | <2s |
| **Total Response Time** | Full response completion | <10s |
| **Tool Execution Time** | Time for tool calls | <1s each |
| **P50 Latency** | 50th percentile | <5s |
| **P95 Latency** | 95th percentile | <15s |
| **P99 Latency** | 99th percentile | <30s |

### 3.2 Latency Measurement

```groovy
class LatencyMeasurement {
    Instant requestStart
    Instant firstTokenReceived
    Instant responseComplete
    List<ToolCallTiming> toolCalls = []

    Duration getTimeToFirstToken() {
        Duration.between(requestStart, firstTokenReceived)
    }

    Duration getTotalResponseTime() {
        Duration.between(requestStart, responseComplete)
    }
}

class ToolCallTiming {
    String toolName
    Instant startTime
    Instant endTime
    Duration duration
}
```

### 3.3 SSE Stream Timing

```groovy
def measureAndAsk(String question) {
    def measurement = new LatencyMeasurement(requestStart: Instant.now())
    def responseContent = new StringBuilder()

    def sseStream = sendChatRequest(question)

    sseStream.eachLine { line ->
        if (line.startsWith("data:")) {
            if (measurement.firstTokenReceived == null) {
                measurement.firstTokenReceived = Instant.now()
            }

            def json = parseJson(line.substring(5))
            if (json.type == "content") {
                responseContent.append(json.content)
            } else if (json.type == "tool_call") {
                measurement.toolCalls << new ToolCallTiming(
                    toolName: json.toolName,
                    startTime: Instant.now()
                )
            } else if (json.type == "tool_result") {
                measurement.toolCalls.last().endTime = Instant.now()
            }
        }
    }

    measurement.responseComplete = Instant.now()

    return new BenchmarkResult(
        response: responseContent.toString(),
        latency: measurement
    )
}
```

### 3.4 Latency Benchmark Scenarios

```groovy
class ResponseTimeSpec extends BaseBenchmarkSpec {

    def "LAT-001: Simple query response time"() {
        given: "health data exists"
        submitStepsForToday(5000)
        waitForProjections()

        when: "asking simple question"
        def result = measureAndAsk("How many steps today?")

        then: "response time is acceptable"
        result.latency.timeToFirstToken.toMillis() < 2000
        result.latency.totalResponseTime.toMillis() < 10000

        and: "record metrics"
        recordLatencyBenchmark("LAT-001", result)
    }

    @Unroll
    def "LAT-002: Response time percentiles across #iterations queries"() {
        given: "health data exists"
        submitStepsForToday(5000)
        waitForProjections()

        when: "running multiple queries"
        def results = (1..iterations).collect {
            measureAndAsk("How many steps today?")
        }

        then: "percentiles meet targets"
        def p50 = percentile(results*.latency*.totalResponseTime, 50)
        def p95 = percentile(results*.latency*.totalResponseTime, 95)

        p50.toMillis() < 5000
        p95.toMillis() < 15000

        where:
        iterations = 10
    }

    def "LAT-003: Tool execution latency"() {
        given: "health data for multiple metrics"
        submitStepsForToday(5000)
        submitActiveCalories(300)
        submitSleepForLastNight(420)
        waitForProjections()

        when: "asking multi-tool question"
        def result = measureAndAsk("Give me a complete health summary for today")

        then: "each tool call is fast"
        result.latency.toolCalls.every { it.duration.toMillis() < 1000 }

        and: "total tool time is reasonable"
        def totalToolTime = result.latency.toolCalls.sum { it.duration.toMillis() }
        totalToolTime < 3000
    }
}
```

**Latency scenario list:**
1. `LAT-001`: Simple query TTFT
2. `LAT-002`: Simple query total time
3. `LAT-003`: Complex query TTFT
4. `LAT-004`: Complex query total time
5. `LAT-005`: Tool execution time (single tool)
6. `LAT-006`: Tool execution time (multi-tool)
7. `LAT-007`: P50/P95/P99 percentiles
8. `LAT-008`: Cold start latency
9. `LAT-009`: Warm cache latency
10. `LAT-010`: Concurrent request latency

---

## 4. Implementation Plan

### Phase 1: Infrastructure (Days 1-2)

**Tasks:**
1. Create `BaseBenchmarkSpec.groovy` with:
   - Token usage extraction from ChatResponse
   - Latency measurement helpers
   - Quality evaluation integration
   - Report data collection

2. Create `BenchmarkResult.groovy`:
   ```groovy
   @Builder
   class BenchmarkResult {
       String scenarioId
       String question
       String response
       TokenUsage tokenUsage
       CostEstimate costEstimate
       LatencyMeasurement latency
       QualityEvaluation quality
       Instant timestamp
   }
   ```

3. Create `BenchmarkReporter.groovy`:
   - Aggregate results across all tests
   - Generate JSON report
   - Generate Markdown summary
   - Calculate aggregate statistics

### Phase 2: Quality Tests (Days 3-4)

**Tasks:**
1. Implement `HappyPathQualitySpec.groovy`:
   - 25 happy path quality scenarios
   - Use existing `HealthDataEvaluator` for LLM-as-Judge
   - Record quality scores per scenario

2. Create quality metrics aggregation:
   - Accuracy rate calculation
   - Hallucination rate calculation
   - Tool selection correctness

### Phase 3: Cost Tests (Days 5-6)

**Tasks:**
1. Implement `TokenUsageExtractor.java`:
   - Extract tokens from Spring AI ChatResponse
   - Handle streaming vs non-streaming responses

2. Implement `TokenUsageSpec.groovy`:
   - 10 cost benchmark scenarios
   - Token usage tracking per scenario
   - Cost estimation calculations

3. Create cost aggregation:
   - Average tokens per query type
   - Cost per query type
   - Total benchmark cost

### Phase 4: Latency Tests (Days 7-8)

**Tasks:**
1. Implement `LatencyMeasurement.groovy`:
   - SSE stream timing
   - Tool call timing
   - TTFT calculation

2. Implement `ResponseTimeSpec.groovy`:
   - 10 latency benchmark scenarios
   - Percentile calculations
   - Concurrent request handling

### Phase 5: Reporting & CI Integration (Days 9-10)

**Tasks:**
1. Implement `BenchmarkReporter.groovy`:
   - JSON report generation
   - Markdown summary generation
   - Historical comparison support

2. Create Gradle task:
   ```kotlin
   tasks.register<Test>("benchmarkTest") {
       description = "Run AI benchmark tests"
       group = "verification"
       include("**/benchmark/**")
       systemProperty("spring.profiles.active", "test,evaluation")
       outputs.dir("build/reports/benchmark")
   }
   ```

3. Add CI workflow for benchmarks

---

## 5. Report Format

### JSON Report Structure

```json
{
  "timestamp": "2025-01-25T10:30:00Z",
  "model": "gemini-3-flash-preview",
  "testSuite": "Happy Path Benchmarks v1.0",
  "summary": {
    "totalScenarios": 45,
    "passed": 43,
    "failed": 2,
    "qualityScore": 0.956,
    "averageLatencyMs": 4500,
    "p95LatencyMs": 12000,
    "totalTokensUsed": 125000,
    "estimatedCostUsd": 0.42
  },
  "quality": {
    "accuracyRate": 0.96,
    "hallucinationRate": 0.02,
    "toolCallSuccessRate": 0.98,
    "languageConsistencyRate": 1.0
  },
  "cost": {
    "averageInputTokens": 1800,
    "averageOutputTokens": 350,
    "averageTotalTokens": 2150,
    "averageCostUsd": 0.0095,
    "totalCostUsd": 0.42
  },
  "latency": {
    "averageTtftMs": 1200,
    "averageTotalMs": 4500,
    "p50Ms": 3800,
    "p95Ms": 12000,
    "p99Ms": 18000
  },
  "scenarios": [
    {
      "id": "HP-Q-001",
      "name": "Steps query - single day",
      "category": "quality",
      "passed": true,
      "inputTokens": 1650,
      "outputTokens": 280,
      "ttftMs": 980,
      "totalTimeMs": 3200,
      "qualityScore": 1.0,
      "evaluatorFeedback": "YES - Response correctly states 8500 steps"
    }
  ]
}
```

### Markdown Summary

```markdown
# AI Benchmark Report - 2025-01-25

## Summary
| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Quality Score | 95.6% | ≥95% | ✅ |
| Accuracy Rate | 96% | ≥95% | ✅ |
| Hallucination Rate | 2% | ≤2% | ✅ |
| Avg Latency | 4.5s | <5s | ✅ |
| P95 Latency | 12s | <15s | ✅ |
| Avg Cost/Query | $0.0095 | <$0.01 | ✅ |

## Quality Breakdown
- Single Metric Queries: 10/10 passed
- Date Range Queries: 5/5 passed
- Multi-Tool Queries: 4/5 passed
- Natural Language: 5/5 passed

## Cost Analysis
| Query Type | Avg Input | Avg Output | Avg Cost |
|------------|-----------|------------|----------|
| Simple | 1,500 | 200 | $0.006 |
| Complex | 2,500 | 500 | $0.015 |
| Multi-tool | 3,000 | 600 | $0.019 |

## Latency Distribution
| Percentile | Value |
|------------|-------|
| P50 | 3.8s |
| P95 | 12s |
| P99 | 18s |
```

---

## 6. CI/CD Integration

### Gradle Configuration

```kotlin
// integration-tests/build.gradle.kts

tasks.register<Test>("benchmarkTest") {
    description = "Run AI benchmark tests with quality, cost, and latency metrics"
    group = "verification"

    useJUnitPlatform()

    include("**/benchmark/**")

    systemProperty("spring.profiles.active", "test,evaluation")

    environment("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY") ?: "")

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }

    outputs.dir("$buildDir/reports/benchmark")

    doLast {
        println("Benchmark report: $buildDir/reports/benchmark/report.json")
    }
}
```

### GitHub Actions Workflow

```yaml
name: AI Benchmarks

on:
  schedule:
    - cron: '0 6 * * 1'  # Weekly on Monday at 6 AM
  workflow_dispatch:

jobs:
  benchmark:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run benchmarks
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
        run: ./gradlew :integration-tests:benchmarkTest

      - name: Upload benchmark report
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-report
          path: integration-tests/build/reports/benchmark/

      - name: Comment on PR (if applicable)
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const report = JSON.parse(fs.readFileSync('integration-tests/build/reports/benchmark/report.json'));
            const summary = `## AI Benchmark Results
            - Quality Score: ${report.summary.qualityScore * 100}%
            - Avg Latency: ${report.summary.averageLatencyMs}ms
            - Est. Cost: $${report.summary.estimatedCostUsd.toFixed(4)}`;
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: summary
            });
```

---

## 7. Success Criteria

### Quality Gates

| Metric | Minimum | Target |
|--------|---------|--------|
| Overall Quality Score | 90% | 95% |
| Accuracy Rate | 92% | 96% |
| Hallucination Rate | <5% | <2% |
| Tool Call Success | 95% | 98% |

### Performance Gates

| Metric | Minimum | Target |
|--------|---------|--------|
| TTFT (P50) | <3s | <2s |
| Total Response (P95) | <20s | <15s |
| Tool Execution | <2s each | <1s each |

### Cost Gates

| Metric | Maximum |
|--------|---------|
| Simple Query | $0.02 |
| Complex Query | $0.05 |
| Full Benchmark Suite | $5.00 |

---

## 8. File List for Implementation

### New Files to Create

1. `integration-tests/src/test/groovy/com/healthassistant/benchmark/BaseBenchmarkSpec.groovy`
2. `integration-tests/src/test/groovy/com/healthassistant/benchmark/BenchmarkResult.groovy`
3. `integration-tests/src/test/groovy/com/healthassistant/benchmark/BenchmarkReporter.groovy`
4. `integration-tests/src/test/groovy/com/healthassistant/benchmark/quality/HappyPathQualitySpec.groovy`
5. `integration-tests/src/test/groovy/com/healthassistant/benchmark/quality/QualityMetrics.groovy`
6. `integration-tests/src/test/groovy/com/healthassistant/benchmark/cost/TokenUsageSpec.groovy`
7. `integration-tests/src/test/groovy/com/healthassistant/benchmark/cost/CostCalculator.groovy`
8. `integration-tests/src/test/groovy/com/healthassistant/benchmark/latency/ResponseTimeSpec.groovy`
9. `integration-tests/src/test/groovy/com/healthassistant/benchmark/latency/LatencyMetrics.groovy`
10. `integration-tests/src/test/java/com/healthassistant/benchmark/TokenUsageExtractor.java`

### Files to Modify

1. `integration-tests/build.gradle.kts` - Add `benchmarkTest` task

---

## 9. Running Benchmarks

```bash
# Run all benchmarks
GEMINI_API_KEY=your-key ./gradlew :integration-tests:benchmarkTest

# Run only quality benchmarks
GEMINI_API_KEY=your-key ./gradlew :integration-tests:benchmarkTest --tests "*quality*"

# Run only cost benchmarks
GEMINI_API_KEY=your-key ./gradlew :integration-tests:benchmarkTest --tests "*TokenUsageSpec"

# Run only latency benchmarks
GEMINI_API_KEY=your-key ./gradlew :integration-tests:benchmarkTest --tests "*ResponseTimeSpec"

# View report
open integration-tests/build/reports/benchmark/report.html
```

---

## 10. Next Steps

1. Review and approve this plan
2. Create the benchmark infrastructure (Phase 1)
3. Implement quality tests (Phase 2)
4. Implement cost tracking (Phase 3)
5. Implement latency measurement (Phase 4)
6. Create reporting and CI integration (Phase 5)
7. Run initial benchmarks and establish baselines
8. Document baseline metrics for future comparisons
