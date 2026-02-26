---
name: pipeline-ai
description: Pełny pipeline z testami ewaluacyjnymi AI - używaj gdy dotykasz AssistantService, HealthTools, promptów, import AI lub guardrails
argument-hint: "[spec-name]"
---

# AI Development Pipeline

Pipeline dla zmian związanych z AI (assistant, import, guardrails, prompt injection).
Opcjonalny argument: nazwa konkretnego spec do uruchomienia (np. `AiMultiToolQuerySpec`).

## Instrukcje

Wykonaj kolejno:

### Krok 1 - Kompilacja + PMD + SpotBugs

```bash
./gradlew build -x test
```

Jeśli BUILD FAILED → zatrzymaj się i napraw.

### Krok 2 - Testy integracyjne (bez evaluation)

```bash
./gradlew :integration-tests:test
```

### Krok 3 - Testy ewaluacyjne AI

Jeśli podano argument (nazwę spec):
```bash
./gradlew :integration-tests:evaluationTest --tests "*<argument>*"
```

Jeśli brak argumentu - uruchom wszystkie:
```bash
./gradlew :integration-tests:evaluationTest
```

Czas: ~20 minut. Pokaż wyniki per kategoria (Mutation, Hallucination, PromptInjection, itp.)

### Krok 4 - SonarQube

Wywołaj `/sonar all`.

### Krok 5 - Code Review

Wywołaj skill `code-review`.

### Podsumowanie

Wyświetl:
- ✅/❌ Build
- ✅/❌ Testy integracyjne
- ✅/❌ Testy ewaluacyjne AI (passed/failed/skipped per spec)
- ✅/❌ SonarQube Quality Gate
- ✅/❌ Code Review

> **Uwaga**: Wyczyść flagę dirty po zakończeniu: `rm -f /tmp/ha-needs-check`
