#!/bin/bash
# Uruchom aplikację z GraalVM tracing agent + wywołaj wszystkie endpointy
# Cel: wygenerować kompletne reflection hints dla native image
#
# Użycie: ./scripts/run-with-agent.sh
#
# Wymagania:
#   - Postgres na porcie 5433 (lub zmień DB_URL)
#   - GraalVM 25 na ścieżce poniżej

set -e

JAVA_CMD="/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home/bin/java"
AGENT_DIR="src/main/resources/META-INF/native-image/com.healthassistant/health-assistant"
JAR="build/libs/health-assistant-event-collector-1.0.0.jar"

# --- env vars ---
export HMAC_DEVICES_JSON='{"test-device-1":"dGVzdC1zZWNyZXQtMTIz"}'
export DB_URL="jdbc:postgresql://localhost:5433/health_assistant"
export DB_USER=postgres
export DB_PASSWORD=postgres
export GOOGLE_FIT_API_URL="https://www.googleapis.com/fitness/v1"
export GOOGLE_FIT_CLIENT_ID="dummy"
export GOOGLE_FIT_CLIENT_SECRET="dummy"
export GOOGLE_FIT_REFRESH_TOKEN="dummy"
export SPRING_PROFILES_ACTIVE=local

echo "Starting app with tracing agent..."
"$JAVA_CMD" \
  -agentlib:native-image-agent=config-merge-dir="$AGENT_DIR" \
  -jar "$JAR" &

APP_PID=$!
echo "App PID: $APP_PID"

# Czekaj na start
echo "Waiting for startup..."
for i in $(seq 1 30); do
  if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
    echo "App is UP after ${i}s"
    break
  fi
  sleep 1
done

# --- HMAC signing ---
DEVICE_ID="test-device-1"
SECRET=$(echo "dGVzdC1zZWNyZXQtMTIz" | base64 --decode)

sign_and_call() {
  local method=$1
  local path=$2
  local body=${3:-""}

  local ts=$(date -u +%s)
  local nonce=$(uuidgen | tr -d '-' | tr '[:upper:]' '[:lower:]')
  local sign_str="${method}\n${path}\n${ts}\n${nonce}\n${DEVICE_ID}\n${body}"
  local sig=$(printf "$sign_str" | openssl dgst -sha256 -hmac "$SECRET" -binary | base64)

  local http_code
  if [ -n "$body" ]; then
    http_code=$(curl -s -o /tmp/api_response.json -w "%{http_code}" \
      -X "$method" \
      -H "Content-Type: application/json" \
      -H "X-Device-Id: $DEVICE_ID" \
      -H "X-Timestamp: $ts" \
      -H "X-Nonce: $nonce" \
      -H "X-Signature: $sig" \
      -d "$body" \
      "http://localhost:8080${path}")
  else
    http_code=$(curl -s -o /tmp/api_response.json -w "%{http_code}" \
      -X "$method" \
      -H "X-Device-Id: $DEVICE_ID" \
      -H "X-Timestamp: $ts" \
      -H "X-Nonce: $nonce" \
      -H "X-Signature: $sig" \
      "http://localhost:8080${path}")
  fi

  echo "$method $path → $http_code"
  cat /tmp/api_response.json | head -c 100
  echo ""
}

echo ""
echo "=== Calling all endpoints ==="

sign_and_call GET "/actuator/health"
sign_and_call GET "/v1/daily-summaries/2026-04-19"
sign_and_call GET "/v1/daily-summaries/range?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/steps/daily/2026-04-19"
sign_and_call GET "/v1/steps/daily/range?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/workouts?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/sleep/range?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/meals/range?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/meals/catalog?q=test&limit=5"
sign_and_call GET "/v1/weight/latest"
sign_and_call GET "/v1/weight/range?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/heartrate/range?from=2026-04-01&to=2026-04-19"
sign_and_call GET "/v1/medical-exams"
sign_and_call GET "/v1/medical-exams/types"
sign_and_call GET "/v1/medical-exams/marker-trend?markerCode=GLUCOSE"

echo ""
echo "Stopping app (saving agent config)..."
kill $APP_PID
wait $APP_PID 2>/dev/null || true

echo ""
echo "=== Agent config saved to: $AGENT_DIR ==="
ls -la "$AGENT_DIR"
echo ""
echo "Now rebuild: ./gradlew nativeCompile -x test -x spotbugsMain -x pmdMain"
