#!/bin/bash

# Test script for Health Assistant Event Ingestion API
# Usage: ./test-api.sh [base_url]

set -e

BASE_URL="${1:-http://localhost:8080}"
DEVICE_ID="test-device"
SECRET_B64="dGVzdC1zZWNyZXQtMTIz"  # base64("test-secret-123")

echo "Testing Health Assistant API at $BASE_URL"
echo "============================================"

# Check health endpoint
echo -e "\n1. Checking health..."
curl -s "$BASE_URL/actuator/health" | jq '.' || echo "Health check failed"

# Function to send authenticated request
send_event() {
    local body="$1"
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local nonce=$(uuidgen | tr '[:upper:]' '[:lower:]')
    
    # Build canonical string
    local canonical="POST
/v1/ingest/events
$timestamp
$nonce
$DEVICE_ID
$body"
    
    # Calculate HMAC signature
    local secret=$(echo "$SECRET_B64" | base64 -d 2>/dev/null || echo "$SECRET_B64" | base64 -D)
    local signature=$(echo -n "$canonical" | openssl dgst -sha256 -hmac "$secret" -binary | base64)
    
    echo -e "\n📤 Sending request..."
    echo "Timestamp: $timestamp"
    echo "Nonce: $nonce"
    
    # Send request
    curl -X POST "$BASE_URL/v1/ingest/events" \
        -H "Content-Type: application/json" \
        -H "X-Device-Id: $DEVICE_ID" \
        -H "X-Timestamp: $timestamp" \
        -H "X-Nonce: $nonce" \
        -H "X-Signature: $signature" \
        -d "$body" \
        -w "\nHTTP Status: %{http_code}\n" \
        -s | jq '.' || echo "Request failed"
}

# Test 1: Single steps event
echo -e "\n2. Testing single steps event..."
BODY1=$(cat <<EOF
{
  "events": [
    {
      "idempotencyKey": "user1|healthconnect|steps|$(date +%s)000",
      "type": "StepsBucketedRecorded.v1",
      "occurredAt": "2025-11-09T07:00:00Z",
      "payload": {
        "bucketStart": "2025-11-09T06:00:00Z",
        "bucketEnd": "2025-11-09T07:00:00Z",
        "count": 742,
        "originPackage": "com.heytap.health.international"
      }
    }
  ]
}
EOF
)
send_event "$BODY1"

# Test 2: Batch with multiple event types
echo -e "\n3. Testing batch with multiple events..."
BODY2=$(cat <<EOF
{
  "events": [
    {
      "idempotencyKey": "user1|healthconnect|steps|$(date +%s)001",
      "type": "StepsBucketedRecorded.v1",
      "occurredAt": "2025-11-09T08:00:00Z",
      "payload": {
        "bucketStart": "2025-11-09T07:00:00Z",
        "bucketEnd": "2025-11-09T08:00:00Z",
        "count": 1523,
        "originPackage": "com.heytap.health.international"
      }
    },
    {
      "idempotencyKey": "user1|healthconnect|hr|$(date +%s)002",
      "type": "HeartRateSummaryRecorded.v1",
      "occurredAt": "2025-11-09T08:15:00Z",
      "payload": {
        "bucketStart": "2025-11-09T08:00:00Z",
        "bucketEnd": "2025-11-09T08:15:00Z",
        "avg": 82.5,
        "min": 68,
        "max": 124,
        "samples": 52,
        "originPackage": "com.heytap.health.international"
      }
    }
  ]
}
EOF
)
send_event "$BODY2"

# Test 3: Duplicate detection
echo -e "\n4. Testing duplicate detection (sending same event again)..."
send_event "$BODY1"

# Test 4: Meal logged event
echo -e "\n5. Testing meal logged event..."
BODY3=$(cat <<EOF
{
  "events": [
    {
      "idempotencyKey": "user1|meal|$(date +%s)003",
      "type": "MealLoggedEstimated.v1",
      "occurredAt": "2025-11-09T12:00:00Z",
      "payload": {
        "when": "2025-11-09T12:00:00Z",
        "items": [
          {
            "name": "Chicken Breast",
            "portion": "200g",
            "kcal": 330,
            "protein_g": 62,
            "carbs_g": 0,
            "fat_g": 7.2
          }
        ],
        "total": {
          "kcal": 330,
          "protein_g": 62,
          "carbs_g": 0,
          "fat_g": 7.2
        }
      }
    }
  ]
}
EOF
)
send_event "$BODY3"

echo -e "\n============================================"
echo "✅ Test suite completed!"
echo ""
echo "📖 View API documentation: $BASE_URL/swagger-ui.html"
echo "🏥 View health status: $BASE_URL/actuator/health"
echo "📊 View metrics: $BASE_URL/actuator/prometheus"

