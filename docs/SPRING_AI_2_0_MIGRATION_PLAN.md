# Spring AI 2.0 M2 Migration Plan

## Overview

Migration from **Spring AI 1.1.2** to **Spring AI 2.0.0-M2**

| Aspect | Current | Target |
|--------|---------|--------|
| Spring AI | 1.1.2 | 2.0.0-M2 |
| Spring Boot | 3.3.5 | 4.0.x |
| Spring Framework | 6.x | 7.0.x |
| Java | 21 | 21 (no change) |

---

## ⚠️ CRITICAL: Platform Upgrade Required

**Spring AI 2.0.0-M2 requires Spring Boot 4.0 and Spring Framework 7.0**

This is a major platform upgrade that affects the entire application. Before proceeding with Spring AI migration, the application must be upgraded to Spring Boot 4.0.

### Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Spring Boot 4.0 compatibility | HIGH | Staged migration, comprehensive testing |
| Spring Framework 7.0 API changes | MEDIUM | Review deprecated APIs |
| Third-party library compatibility | MEDIUM | Verify all dependencies support Boot 4.0 |
| Database/JPA changes | LOW | Hibernate 7 compatibility check |

---

## Phase 1: Pre-Migration Assessment

### 1.1 Dependency Compatibility Check

Verify all dependencies support Spring Boot 4.0:

| Dependency | Current Version | Boot 4.0 Compatible? | Action |
|------------|-----------------|---------------------|--------|
| spring-modulith | 1.3.1 | TBD | Check for 2.x version |
| spring-cloud-openfeign | 4.1.3 | TBD | Check for 5.x version |
| springdoc-openapi | 2.6.0 | TBD | Check for 3.x version |
| flyway | (managed) | YES | Verify |
| postgresql | 42.7.4 | YES | No change |
| mapstruct | 1.5.5 | YES | No change |
| lombok | (managed) | YES | No change |
| caffeine | (managed) | YES | No change |
| testcontainers | (managed) | YES | No change |
| spock | (managed) | YES | Verify Groovy compatibility |

### 1.2 Files to Modify

Based on codebase analysis, the following files use Spring AI directly:

**Configuration:**
- `build.gradle.kts` - BOM version update
- `integration-tests/build.gradle.kts` - Test BOM version update
- `AssistantConfiguration.java` - ChatClient builder configuration

**ChatClient Usage:**
- `AssistantService.java` - Streaming chat with conversation history
- `AiDailySummaryService.java` - Non-streaming chat calls

**Advisors:**
- `ChatGuardrailAdvisor.java` - CallAdvisor, StreamAdvisor implementation

**Tools:**
- `HealthTools.java` - @Tool annotations (8 tools)

**Image Extractors (Multi-modal):**
- `WorkoutImageExtractor.java` - Vision + structured output
- `MealContentExtractor.java` - Multi-image + structured output
- `SleepImageExtractor.java` - Vision + structured output
- `WeightImageExtractor.java` - Vision + structured output

**Message Handling:**
- `ConversationService.java` - SystemMessage, UserMessage, AssistantMessage

---

## Phase 2: Spring Boot 4.0 Upgrade

### 2.1 Update Gradle Plugins

```kotlin
// build.gradle.kts - BEFORE
plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

// build.gradle.kts - AFTER
plugins {
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.2.x"  // Check latest
}
```

### 2.2 Update Spring Modulith BOM

```kotlin
// BEFORE
mavenBom("org.springframework.modulith:spring-modulith-bom:1.3.1")

// AFTER - Check for Boot 4.0 compatible version
mavenBom("org.springframework.modulith:spring-modulith-bom:2.x.x")
```

### 2.3 Jakarta EE Namespace Changes (if not already migrated)

Spring Boot 4.0 uses Jakarta EE 11. Verify all imports use `jakarta.*` namespace:

```java
// Already using jakarta.* - no changes needed
import jakarta.validation.Valid;
import jakarta.persistence.Entity;
```

### 2.4 Spring Framework 7.0 API Changes

Check for deprecated APIs that may be removed in Spring Framework 7.0:
- Review `@Autowired` usage (constructor injection preferred)
- Check `RestTemplate` usage (WebClient recommended)
- Verify reactive types compatibility

---

## Phase 3: Spring AI 2.0 Migration

### 3.1 Update Spring AI BOM

```kotlin
// build.gradle.kts - BEFORE
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.2")
    }
}

// build.gradle.kts - AFTER
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
    }
}
```

### 3.2 Repository Configuration

Spring AI 2.0 M2 artifacts are in the milestone repository (already configured):

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
}
```

### 3.3 Advisor API Changes

**Current code is already compatible!** The codebase uses the new API:

| Old API (pre-M3) | New API (M3+) | Current Code |
|------------------|---------------|--------------|
| `CallAroundAdvisor` | `CallAdvisor` | ✅ Uses `CallAdvisor` |
| `StreamAroundAdvisor` | `StreamAdvisor` | ✅ Uses `StreamAdvisor` |
| `AdvisedRequest` | `ChatClientRequest` | ✅ Uses `ChatClientRequest` |
| `AdvisedResponse` | `ChatClientResponse` | ✅ Uses `ChatClientResponse` |

**No changes needed for `ChatGuardrailAdvisor.java`**

### 3.4 Tool Registration Changes (M8+)

The `tools()` method was renamed for clarity. Check `AssistantConfiguration.java`:

```java
// BEFORE (1.1.x)
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultTools(healthTools)  // May need to change
            .defaultAdvisors(chatGuardrailAdvisor)
            .build();
}

// AFTER (2.0) - Check actual method name
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultToolCallbacks(healthTools)  // OR toolCallbacks()
            .defaultAdvisors(chatGuardrailAdvisor)
            .build();
}
```

**Action:** Verify actual method name in Spring AI 2.0 M2 API. The change was:
- `tools(String... toolNames)` → `toolNames(String... toolNames)`
- `tools(ToolCallback... callbacks)` → `toolCallbacks(ToolCallback...)`

### 3.5 @Tool Annotation

**No changes expected.** The `@Tool` annotation introduced in M6 remains stable.

Current usage in `HealthTools.java`:
```java
@Tool(name = "getStepsData",
      description = "Retrieves user's step data...")
public Object getStepsData(String startDate, String endDate) { ... }
```

### 3.6 Temperature Configuration

**BREAKING CHANGE:** Default temperature removed in 2.0.

Add explicit temperature configuration:

```yaml
# application.yml - ADD
spring:
  ai:
    google:
      genai:
        chat:
          options:
            temperature: 0.7  # Explicit setting required
```

Or programmatically:
```java
chatClient.prompt()
    .options(ChatOptionsBuilder.builder()
        .withTemperature(0.7)
        .build())
    .messages(messages)
    .stream()
```

### 3.7 Google GenAI SDK Update

Spring AI 2.0 M1 updated Google GenAI SDK to 1.30.0. Verify compatibility:

```kotlin
// Dependency remains the same
implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
```

**Potential changes:**
- Model names (check if `gemini-3-flash-preview` is still valid)
- New `ThinkingLevel` support in `ThinkingConfig`
- Updated authentication flow

### 3.8 Structured Output Changes

Current code uses `.entity(Class)` for structured output:

```java
// WorkoutImageExtractor.java
AiWorkoutExtractionResponse response = chatClient.prompt()
    .system(buildSystemPrompt())
    .user(userSpec -> userSpec
        .text(buildUserPrompt())
        .media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes))
    )
    .call()
    .entity(AiWorkoutExtractionResponse.class);
```

**Check:** Mistral AI added native JSON schema support in 2.0 M2. Verify if Google GenAI has similar changes and if `.entity()` behavior changed.

### 3.9 Message Types

Current imports in `ConversationService.java`:
```java
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
```

**Expected:** No changes to message types package location.

---

## Phase 4: Integration Tests Update

### 4.1 Update Test BOM

```kotlin
// integration-tests/build.gradle.kts - BEFORE
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.0")
    }
}

// integration-tests/build.gradle.kts - AFTER
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
    }
}
```

### 4.2 Test Classes to Verify

All AI-related tests must pass after migration:

**Core Tests:**
- `AssistantSpec.groovy`
- `ConversationHistorySpec.groovy`
- `DailySummarySpec.groovy`

**Import Tests:**
- `WorkoutImportSpec.groovy`
- `MealImportSpec.groovy`
- `SleepImportSpec.groovy`
- `MealImportDraftSpec.groovy`

**Evaluation Tests:**
- `AiHallucinationSpec.groovy`
- `AiToolErrorHandlingSpec.groovy`
- `AiConversationAccuracySpec.groovy`
- `AiContentFilteringSpec.groovy`
- `AiPromptInjectionSpec.groovy`
- `AiStreamErrorRecoverySpec.groovy`
- `AiConcurrentRequestsSpec.groovy`
- `AiMultiToolQuerySpec.groovy`
- `AiDailySummaryEvaluationSpec.groovy`

---

## Phase 5: Implementation Steps

### Step-by-Step Migration Order

1. **Create feature branch**
   ```bash
   git checkout -b feature/spring-ai-2.0-migration
   ```

2. **Update Spring Boot to 4.0.x**
   - Update `build.gradle.kts` plugin version
   - Fix any immediate compilation errors
   - Run `./gradlew build -x test`

3. **Update all dependent BOMs**
   - Spring Modulith
   - Spring Cloud OpenFeign
   - Verify all dependencies compile

4. **Update Spring AI BOM to 2.0.0-M2**
   - Change version in `build.gradle.kts`
   - Change version in `integration-tests/build.gradle.kts`

5. **Fix compilation errors**
   - Tool registration method names
   - Any removed/renamed APIs

6. **Add explicit temperature configuration**
   - Update `application.yml`
   - Verify all ChatClient usages

7. **Run unit tests**
   ```bash
   ./gradlew test
   ```

8. **Run integration tests**
   ```bash
   ./gradlew :integration-tests:test
   ```

9. **Fix failing tests**
   - Update test expectations if API behavior changed

10. **Manual testing**
    - Test `/v1/assistant/chat` endpoint
    - Test image import endpoints
    - Test daily summary generation

11. **Performance testing**
    ```bash
    ./gradlew :integration-tests:benchmarkTest
    ```

---

## Phase 6: Rollback Plan

If migration fails:

1. **Git rollback**
   ```bash
   git checkout main
   git branch -D feature/spring-ai-2.0-migration
   ```

2. **Alternative: Stay on 1.1.x**
   - Spring AI 1.1.x will continue to receive updates
   - Wait for Spring Boot 4.0 GA and Spring AI 2.0 GA

---

## Phase 7: Post-Migration Validation

### Checklist

- [ ] Application starts successfully
- [ ] `/actuator/health` returns UP
- [ ] `/v1/assistant/chat` streaming works
- [ ] Tool calls (function calling) work correctly
- [ ] Conversation history is maintained
- [ ] Image extraction (workout, meal, sleep, weight) works
- [ ] Daily summary AI generation works
- [ ] Guardrail advisor blocks unsafe content
- [ ] All integration tests pass
- [ ] Performance is acceptable (benchmark tests)
- [ ] No memory leaks observed
- [ ] Prometheus metrics are collected

---

## Appendix A: API Change Summary

| Component | 1.1.x API | 2.0 M2 API | Impact |
|-----------|-----------|------------|--------|
| Advisor interfaces | CallAdvisor, StreamAdvisor | No change | ✅ Compatible |
| Request/Response | ChatClientRequest, ChatClientResponse | No change | ✅ Compatible |
| Tool registration | `defaultTools()` | `defaultToolCallbacks()` | 🔄 Check |
| @Tool annotation | `@Tool(name, description)` | No change | ✅ Compatible |
| Message types | SystemMessage, UserMessage, AssistantMessage | No change | ✅ Compatible |
| ChatClient.prompt() | Fluent API | No change | ✅ Compatible |
| Streaming | `.stream().chatResponse()` | No change | ✅ Compatible |
| Structured output | `.entity(Class)` | No change expected | ✅ Compatible |

---

## Appendix B: Files to Modify (Summary)

### Mandatory Changes

| File | Change |
|------|--------|
| `build.gradle.kts` | Spring Boot 4.0.x, Spring AI 2.0.0-M2 |
| `integration-tests/build.gradle.kts` | Spring AI 2.0.0-M2 |
| `application.yml` | Add explicit `temperature` config |

### Potential Changes (verify API)

| File | Potential Change |
|------|------------------|
| `AssistantConfiguration.java` | `defaultTools()` → `defaultToolCallbacks()` |

### No Changes Expected

| File | Reason |
|------|--------|
| `ChatGuardrailAdvisor.java` | Already uses new API |
| `HealthTools.java` | @Tool annotation unchanged |
| `AssistantService.java` | ChatClient API unchanged |
| `ConversationService.java` | Message types unchanged |
| `WorkoutImageExtractor.java` | Media API unchanged |
| `MealContentExtractor.java` | Media API unchanged |
| `SleepImageExtractor.java` | Media API unchanged |
| `WeightImageExtractor.java` | Media API unchanged |
| `AiDailySummaryService.java` | ChatClient API unchanged |

---

## Appendix C: Timeline Recommendation

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Assessment | 1 day | None |
| Phase 2: Spring Boot 4.0 | 2-3 days | Phase 1 |
| Phase 3: Spring AI 2.0 | 1-2 days | Phase 2 |
| Phase 4: Test updates | 1-2 days | Phase 3 |
| Phase 5: Implementation | Included above | - |
| Phase 6: Rollback prep | 0.5 day | Phase 3 |
| Phase 7: Validation | 1-2 days | Phase 5 |

**Total estimated effort: 5-10 days**

---

## Sources

- [Spring AI Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
- [Spring AI GitHub Releases](https://github.com/spring-projects/spring-ai/releases)
- [Arconia Spring AI Migrations](https://github.com/arconia-io/arconia-migrations/blob/main/docs/spring-ai.md)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Tools Migration](https://docs.spring.io/spring-ai/reference/api/tools-migration.html)
- [Google GenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html)
- [Spring AI 2.0.0-M1 Blog](https://spring.io/blog/2025/12/11/spring-ai-2-0-0-M1-available-now/)
