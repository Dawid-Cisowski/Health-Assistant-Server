#!/bin/bash
# Stop hook - uruchamia quality check gdy kod był edytowany w tej sesji
# Jeśli build się nie powiedzie, Claude zobaczy błędy i musi je naprawić przed zakończeniem

FLAG_FILE=/tmp/ha-needs-check

if [ ! -f "$FLAG_FILE" ]; then
    exit 0
fi

rm -f "$FLAG_FILE"

PROJECT_DIR="/Users/dawidcidowski/IdeaProjects/HealthAssistantServer"
cd "$PROJECT_DIR" || exit 1

echo "╔══════════════════════════════════════════════════════╗"
echo "║  Auto Quality Check (kompilacja + PMD + SpotBugs)   ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

./gradlew build -x test 2>&1
EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -ne 0 ]; then
    echo "✗ QUALITY CHECK FAILED - napraw błędy przed zakończeniem zadania!"
    echo "  Uruchom /pipeline żeby zobaczyć pełny raport."
else
    echo "✓ Quality check PASSED"
    echo "  Rozważ uruchomienie /pipeline (pełne testy) lub /pipeline-ai (zmiany AI)."
fi

exit $EXIT_CODE
