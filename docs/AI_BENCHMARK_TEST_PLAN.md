# AI Benchmark Test Plan - Happy Path

## Overview

Plan testów benchmark dla AI Health Assistant. **10 testów**, każdy mierzy **wszystkie metryki** (quality, cost, time). Porównanie **Gemini 3 Pro vs Gemini 3 Flash**.

## Metryki mierzone w każdym teście

| Kategoria | Metryka | Opis |
|-----------|---------|------|
| **Quality** | Pass/Fail | Czy odpowiedź jest poprawna (asercje lub LLM-as-Judge) |
| **Cost** | Input Tokens | Tokeny w promptcie |
| **Cost** | Output Tokens | Tokeny w odpowiedzi |
| **Cost** | Estimated Cost | Szacowany koszt USD |
| **Time** | Response Time | Całkowity czas odpowiedzi (ms) |
| **Time** | TTFT | Time to First Token (ms) - dla SSE |

---

## 10 Testów Benchmark

### Test 1: Simple Chat - Steps Query
**Scenariusz**: Proste pytanie o kroki

```groovy
def "BM-01: Simple steps query"() {
    given: "8500 kroków zapisanych na dziś"
    submitStepsForToday(8500)
    waitForProjections()

    when: "pytanie o kroki"
    def result = benchmarkChat("How many steps did I take today?")

    then: "odpowiedź zawiera 8500"
    result.response.contains("8500") || result.response.contains("8,500")

    and: "metryki zapisane"
    recordBenchmark("BM-01", "Simple steps query", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (zawiera 8500)
- Tokens: ~1500 input, ~100 output
- Time: <5s

---

### Test 2: Multi-turn Conversation
**Scenariusz**: Rozmowa z historią (2 tury)

```groovy
def "BM-02: Multi-turn conversation"() {
    given: "dane zdrowotne"
    submitStepsForToday(10000)
    submitSleepForLastNight(420) // 7h

    when: "pierwsza wiadomość"
    askAssistant("How many steps today?")

    and: "druga wiadomość z kontekstem"
    def result = benchmarkChat("And how did I sleep last night?")

    then: "odpowiedź zawiera ~7 godzin"
    result.response.toLowerCase().contains("7") || result.response.contains("420")

    and: "metryki zapisane"
    recordBenchmark("BM-02", "Multi-turn conversation", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (zawiera info o śnie)
- Tokens: ~2500 input (historia), ~150 output
- Time: <8s

---

### Test 3: AI Daily Summary Generation
**Scenariusz**: Generowanie podsumowania dnia przez AI

```groovy
def "BM-03: AI daily summary generation"() {
    given: "pełne dane zdrowotne na dziś"
    submitStepsForToday(12000)
    submitSleepForLastNight(480) // 8h
    submitActiveCalories(450)
    submitMeal("Oatmeal", "BREAKFAST", 350, 15, 8, 50)
    waitForProjections()

    when: "żądanie AI summary"
    def result = benchmarkAiSummary(today)

    then: "summary zawiera dane"
    result.response.length() > 50

    and: "metryki zapisane"
    recordBenchmark("BM-03", "AI daily summary", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (sensowne podsumowanie)
- Tokens: ~800 input, ~200 output
- Time: <5s

---

### Test 4: Meal Import - Simple (Banana)
**Scenariusz**: Import prostego posiłku z opisu tekstowego

```groovy
def "BM-04: Meal import - banana"() {
    when: "import banana"
    def result = benchmarkMealImport("banan")

    then: "kalorie ~90-135 kcal"
    def calories = result.parsedResponse.caloriesKcal
    calories >= 90 && calories <= 135

    and: "węglowodany dominujące ~23-34g"
    def carbs = result.parsedResponse.carbohydratesGrams
    carbs >= 23 && carbs <= 34

    and: "metryki zapisane"
    recordBenchmark("BM-04", "Meal import - banana", result)
}
```

**Dane testowe**: Z `MealImportAISpec.groovy`
- Input: "banan"
- Expected: ~112 kcal, ~28g carbs, ~1g protein, ~0.4g fat

**Oczekiwane wartości**:
- Quality: Pass (kalorie w zakresie)
- Tokens: ~500 input, ~100 output
- Time: <3s

---

### Test 5: Meal Import - Complex (Chicken meal)
**Scenariusz**: Import złożonego posiłku

```groovy
def "BM-05: Meal import - chicken with rice"() {
    when: "import złożonego posiłku"
    def result = benchmarkMealImport("200g grillowanego kurczaka, 200g brokuła i 100g ryżu")

    then: "kalorie ~350-750 kcal"
    def calories = result.parsedResponse.caloriesKcal
    calories >= 350 && calories <= 750

    and: "białko wysokie ~35-90g"
    def protein = result.parsedResponse.proteinGrams
    protein >= 35 && protein <= 90

    and: "metryki zapisane"
    recordBenchmark("BM-05", "Meal import - complex", result)
}
```

**Dane testowe**: Z `MealImportAISpec.groovy`
- Input: "200g grillowanego kurczaka, 200g brokuła i 100g ryżu"
- Expected: ~530 kcal, ~71g protein, ~42g carbs

**Oczekiwane wartości**:
- Quality: Pass (makra w zakresie)
- Tokens: ~600 input, ~150 output
- Time: <4s

---

### Test 6: Sleep Import from Screenshot
**Scenariusz**: Import snu ze screenshota (vision)

```groovy
def "BM-06: Sleep import from screenshot"() {
    given: "screenshot sleep_1.png"
    def imageBytes = loadScreenshot("/screenshots/sleep_1.png")

    when: "import via vision API"
    def result = benchmarkSleepImport(imageBytes, "sleep_1.png")

    then: "totalMinutes ~378 (±5%)"
    def totalMinutes = result.parsedResponse.totalSleepMinutes
    totalMinutes >= 359 && totalMinutes <= 397

    and: "metryki zapisane"
    recordBenchmark("BM-06", "Sleep import - screenshot", result)
}
```

**Dane testowe**: Z `SleepImportAISpec.groovy`
- Input: `sleep_1.png` (ohealth screenshot)
- Expected: 378 min (6h18m), score ~67

**Oczekiwane wartości**:
- Quality: Pass (czas snu w zakresie)
- Tokens: ~1000 input (z obrazem), ~100 output
- Time: <8s (vision jest wolniejsze)

---

### Test 7: Polish Language Query
**Scenariusz**: Zapytanie po polsku

```groovy
def "BM-07: Polish language query"() {
    given: "dane zdrowotne"
    submitStepsForToday(7500)
    waitForProjections()

    when: "pytanie po polsku"
    def result = benchmarkChat("Ile kroków zrobiłem dzisiaj?")

    then: "odpowiedź po polsku zawiera 7500"
    (result.response.contains("7500") || result.response.contains("7 500")) &&
    (result.response.contains("krok") || result.response.contains("step"))

    and: "metryki zapisane"
    recordBenchmark("BM-07", "Polish language query", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (odpowiedź po polsku z danymi)
- Tokens: ~1500 input, ~100 output
- Time: <5s

---

### Test 8: Date Recognition - "Yesterday"
**Scenariusz**: Rozpoznawanie daty względnej

```groovy
def "BM-08: Date recognition - yesterday"() {
    given: "sen zapisany na wczoraj"
    submitSleepForDate(yesterday, 450) // 7.5h
    waitForProjections()

    when: "pytanie o wczoraj"
    def result = benchmarkChat("How did I sleep yesterday?")

    then: "odpowiedź zawiera ~7.5 godzin lub 450 minut"
    result.response.contains("7") || result.response.contains("450")

    and: "metryki zapisane"
    recordBenchmark("BM-08", "Date recognition - yesterday", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (poprawna data)
- Tokens: ~1500 input, ~100 output
- Time: <5s

---

### Test 9: Multi-tool Query
**Scenariusz**: Zapytanie wymagające wielu narzędzi

```groovy
def "BM-09: Multi-tool query"() {
    given: "różne dane zdrowotne"
    submitStepsForToday(9000)
    submitActiveCalories(380)
    submitSleepForLastNight(420)
    waitForProjections()

    when: "pytanie o całościowe podsumowanie"
    def result = benchmarkChat("Give me a complete health summary for today")

    then: "odpowiedź zawiera wszystkie metryki"
    result.response.contains("9000") || result.response.contains("9,000")  // steps
    result.response.toLowerCase().contains("7") // hours of sleep
    (result.response.contains("380") || result.response.toLowerCase().contains("calorie"))

    and: "metryki zapisane"
    recordBenchmark("BM-09", "Multi-tool query", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (wszystkie dane obecne)
- Tokens: ~2000 input, ~300 output
- Time: <10s (multiple tool calls)

---

### Test 10: Weekly Summary Query
**Scenariusz**: Zapytanie o zakres dat

```groovy
def "BM-10: Weekly summary query"() {
    given: "kroki przez 7 dni"
    (0..6).each { daysAgo ->
        submitStepsForDate(today.minusDays(daysAgo), 5000 + (daysAgo * 500))
    }
    waitForProjections()
    // Total: 5000+5500+6000+6500+7000+7500+8000 = 45500

    when: "pytanie o tydzień"
    def result = benchmarkChat("How many steps did I take this week in total?")

    then: "odpowiedź zawiera ~45000-46000"
    result.response.contains("45") || result.response.contains("46")

    and: "metryki zapisane"
    recordBenchmark("BM-10", "Weekly summary query", result)
}
```

**Oczekiwane wartości**:
- Quality: Pass (suma w zakresie)
- Tokens: ~2000 input, ~150 output
- Time: <8s

---

## Struktura kodu

```
integration-tests/src/test/groovy/com/healthassistant/benchmark/
├── AiBenchmarkSpec.groovy        # Wszystkie 10 testów
├── BenchmarkResult.groovy        # Data class dla wyników
└── BenchmarkReporter.groovy      # Generowanie raportu
```

### BenchmarkResult.groovy

```groovy
@Builder
class BenchmarkResult {
    String testId
    String testName
    String model              // "gemini-3-pro" lub "gemini-3-flash"

    // Quality
    boolean passed
    String response
    String errorMessage

    // Cost
    long inputTokens
    long outputTokens
    double estimatedCostUsd

    // Time
    long responseTimeMs
    long ttftMs              // Time to First Token (null dla non-streaming)

    Instant timestamp
}
```

### Porównanie modeli

```groovy
@Unroll
def "BM-01: Simple steps query - #modelName"() {
    given: "configured model"
    setModel(modelName)

    and: "health data"
    submitStepsForToday(8500)
    waitForProjections()

    when: "asking about steps"
    def result = benchmarkChat("How many steps did I take today?")

    then: "correct response"
    result.response.contains("8500")

    and: "record benchmark"
    recordBenchmark("BM-01", "Simple steps query", modelName, result)

    where:
    modelName << ["gemini-3-pro-preview", "gemini-3-flash-preview"]
}
```

---

## Raport porównawczy

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                    AI Benchmark Report - 2025-01-25                           ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║ Test                      │ Model        │ Pass │ Tokens    │ Cost   │ Time  ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║ BM-01: Simple steps       │ gemini-3-pro │  ✅  │ 1520/95   │ $0.003 │ 2.1s  ║
║ BM-01: Simple steps       │ gemini-flash │  ✅  │ 1520/90   │ $0.001 │ 1.2s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-02: Multi-turn         │ gemini-3-pro │  ✅  │ 2450/145  │ $0.005 │ 3.5s  ║
║ BM-02: Multi-turn         │ gemini-flash │  ✅  │ 2450/130  │ $0.002 │ 2.1s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-03: AI Summary         │ gemini-3-pro │  ✅  │ 850/210   │ $0.004 │ 2.8s  ║
║ BM-03: AI Summary         │ gemini-flash │  ✅  │ 850/195   │ $0.001 │ 1.5s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-04: Meal - banana      │ gemini-3-pro │  ✅  │ 520/85    │ $0.002 │ 1.8s  ║
║ BM-04: Meal - banana      │ gemini-flash │  ✅  │ 520/80    │ $0.001 │ 1.1s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-05: Meal - complex     │ gemini-3-pro │  ✅  │ 580/120   │ $0.003 │ 2.2s  ║
║ BM-05: Meal - complex     │ gemini-flash │  ✅  │ 580/115   │ $0.001 │ 1.4s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-06: Sleep screenshot   │ gemini-3-pro │  ✅  │ 1050/95   │ $0.004 │ 5.2s  ║
║ BM-06: Sleep screenshot   │ gemini-flash │  ✅  │ 1050/90   │ $0.001 │ 3.8s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-07: Polish query       │ gemini-3-pro │  ✅  │ 1480/105  │ $0.003 │ 2.3s  ║
║ BM-07: Polish query       │ gemini-flash │  ✅  │ 1480/100  │ $0.001 │ 1.4s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-08: Date yesterday     │ gemini-3-pro │  ✅  │ 1510/110  │ $0.003 │ 2.4s  ║
║ BM-08: Date yesterday     │ gemini-flash │  ✅  │ 1510/105  │ $0.001 │ 1.5s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-09: Multi-tool         │ gemini-3-pro │  ✅  │ 2100/280  │ $0.006 │ 6.5s  ║
║ BM-09: Multi-tool         │ gemini-flash │  ✅  │ 2100/260  │ $0.002 │ 4.2s  ║
╠───────────────────────────┼──────────────┼──────┼───────────┼────────┼───────╣
║ BM-10: Weekly summary     │ gemini-3-pro │  ✅  │ 1980/155  │ $0.004 │ 4.1s  ║
║ BM-10: Weekly summary     │ gemini-flash │  ✅  │ 1980/145  │ $0.002 │ 2.8s  ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║ TOTALS                                                                        ║
╠───────────────────────────┬──────────────┬──────┬───────────┬────────┬───────╣
║ gemini-3-pro              │ 10/10 passed │      │ 14040/1400│ $0.037 │ 33.9s ║
║ gemini-3-flash            │ 10/10 passed │      │ 14040/1310│ $0.013 │ 21.0s ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║ WINNER: gemini-3-flash                                                        ║
║ - 65% cheaper ($0.013 vs $0.037)                                              ║
║ - 38% faster (21s vs 34s)                                                     ║
║ - Same quality (10/10 vs 10/10)                                               ║
╚═══════════════════════════════════════════════════════════════════════════════╝
```

---

## Cennik modeli (styczeń 2025)

| Model | Input (per 1M) | Output (per 1M) |
|-------|----------------|-----------------|
| Gemini 3 Pro | $1.25 | $5.00 |
| Gemini 3 Flash | $0.075 | $0.30 |

---

## Uruchamianie

```bash
# Oba modele
GEMINI_API_KEY=your-key ./gradlew :integration-tests:benchmarkTest

# Tylko Pro
GEMINI_API_KEY=your-key GEMINI_MODEL=gemini-3-pro-preview ./gradlew :integration-tests:benchmarkTest

# Tylko Flash
GEMINI_API_KEY=your-key GEMINI_MODEL=gemini-3-flash-preview ./gradlew :integration-tests:benchmarkTest
```

---

## Pliki do utworzenia

1. `integration-tests/src/test/groovy/com/healthassistant/benchmark/AiBenchmarkSpec.groovy` - 10 testów
2. `integration-tests/src/test/groovy/com/healthassistant/benchmark/BenchmarkResult.groovy` - data class
3. `integration-tests/src/test/groovy/com/healthassistant/benchmark/BenchmarkReporter.groovy` - raportowanie

---

## Dane testowe (z istniejących testów)

### Meal Import (z MealImportAISpec)
- **Banana**: "banan" → ~112 kcal, 28g carbs, 1g protein, 0.4g fat
- **Coffee**: "kubek czarnej kawy" → ~5 kcal
- **Chicken**: "200g grillowanego kurczaka, 200g brokuła i 100g ryżu" → ~530 kcal, 71g protein

### Sleep Import (z SleepImportAISpec)
- **sleep_1.png**: 378 min (6h18m), score 67, deep 56min, light 219min, REM 103min
