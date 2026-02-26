---
name: pipeline
description: Uruchamia pełny pipeline jakości - build, testy integracyjne, SonarQube i code review
argument-hint: "[quick|full]"
---

# Development Quality Pipeline

Uruchom pełny pipeline dla HealthAssistant. Argument: `quick` (tylko build) lub `full` (domyślnie - wszystko).

## Instrukcje

Wykonaj poniższe kroki **w kolejności**, zatrzymując się na błędach:

### Krok 1 - Kompilacja + PMD + SpotBugs

```bash
./gradlew build -x test
```

Jeśli BUILD FAILED → zatrzymaj się, pokaż błędy, napraw je.

### Krok 2 - Testy integracyjne (pomiń jeśli argument `quick`)

```bash
./gradlew :integration-tests:test
```

Jeśli testy FAILED → pokaż które testy padły i ich błędy. Zapytaj czy kontynuować mimo błędów.

### Krok 3 - SonarQube (pomiń jeśli argument `quick`)

Wywołaj `/sonar all` żeby sprawdzić quality gate, issues i metryki.

### Krok 4 - Code Review (pomiń jeśli argument `quick`)

Wywołaj skill `code-review` dla aktualnego brancha.

### Podsumowanie

Po wykonaniu wszystkich kroków wyświetl podsumowanie:
- ✅/❌ Build
- ✅/❌ Testy integracyjne (liczba passed/failed/skipped)
- ✅/❌ SonarQube Quality Gate
- ✅/❌ Code Review - kluczowe uwagi

Jeśli coś padło → zaproponuj co naprawić w pierwszej kolejności.

> **Uwaga**: Wyczyść flagę dirty po zakończeniu: `rm -f /tmp/ha-needs-check`
