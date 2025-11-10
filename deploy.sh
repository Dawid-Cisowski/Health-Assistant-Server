#!/usr/bin/env bash
# Deployment script: builds Docker image and deploys to Cloud Run
# Compatible with macOS + Google Cloud SDK

set -euo pipefail

# === CONFIGURATION ===
PROJECT_ID=${PROJECT_ID:-"health-assistant-477621"}     # <-- wpisz swój project ID
REGION=${REGION:-"europe-central2"}                    # region (Warszawa)
REPO=${REPO:-"health-assistant-event-collector"}       # nazwa repozytorium w Artifact Registry
IMAGE=${IMAGE:-"health-assistant-event-collector"}     # nazwa obrazu
SERVICE=${SERVICE:-"health-assistant-event-collector"} # nazwa usługi Cloud Run
PORT=${PORT:-8080}                                     # port aplikacji
TAG=$(date +%Y%m%d-%H%M%S)                             # tag oparty na dacie

# Database configuration (set these or run dbdeploy.sh first)
INSTANCE=${INSTANCE:-"app-db"}                         # Cloud SQL instance name
DB_NAME=${DB_NAME:-"app"}                              # database name

# === HELPER ===
log() { echo -e "\033[1;34m[deploy]\033[0m $*"; }

# === START ===
log "Projekt: $PROJECT_ID  | Region: $REGION  | Usługa: $SERVICE"

# --- enable services ---
log "Aktywuję wymagane API..."
gcloud config set project "$PROJECT_ID"
gcloud services enable artifactregistry.googleapis.com run.googleapis.com cloudbuild.googleapis.com

# --- create repo (idempotent) ---
log "Tworzę (lub sprawdzam) repozytorium Artifact Registry..."
gcloud artifacts repositories create "$REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Docker repo for Cloud Run" || true

# --- build & push image ---
FULL_IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/$IMAGE:$TAG"
log "Buduję i publikuję obraz: $FULL_IMAGE"
gcloud builds submit --tag "$FULL_IMAGE"

# --- get Cloud SQL connection name ---
INSTANCE_CONN=$(gcloud sql instances describe "$INSTANCE" --format='value(connectionName)' 2>/dev/null || echo "")
if [ -z "$INSTANCE_CONN" ]; then
  log "⚠️  Cloud SQL instance '$INSTANCE' nie istnieje. Uruchom najpierw: ./dbdeploy.sh"
  exit 1
fi

# --- deploy to Cloud Run ---
log "Wdrażam usługę do Cloud Run..."
log "Cloud SQL: $INSTANCE_CONN"

# Get HMAC devices from environment or use default
HMAC_DEVICES=${HMAC_DEVICES_JSON:-'{"3d57e55e7c769f97":"NgEB5QeV1GAJ2231CarBgwNls8NKwnTW/sHsqVqZjto="}'}

# Get database username from environment or use default
DB_USER=${DB_USER:-"app_user"}

log "Konfiguruję zmienne środowiskowe..."
log "  • Profile: production"
log "  • Database: $DB_NAME"
log "  • User: $DB_USER"

gcloud run deploy "$SERVICE" \
  --image "$FULL_IMAGE" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --port "$PORT" \
  --cpu=1 --memory=512Mi --min-instances=0 --max-instances=10 \
  --add-cloudsql-instances "$INSTANCE_CONN" \
  --set-env-vars "SPRING_PROFILES_ACTIVE=production" \
  --set-env-vars "SPRING_DATASOURCE_URL=jdbc:postgresql:///$DB_NAME?cloudSqlInstance=$INSTANCE_CONN&socketFactory=com.google.cloud.sql.postgres.SocketFactory" \
  --set-env-vars "SPRING_DATASOURCE_USERNAME=$DB_USER" \
  --set-secrets "SPRING_DATASOURCE_PASSWORD=db-password:latest" \
  --set-env-vars "HMAC_DEVICES_JSON=$HMAC_DEVICES" \
  --set-env-vars "HMAC_TOLERANCE_SEC=600" \
  --set-env-vars "NONCE_CACHE_TTL_SEC=600" \
  --set-env-vars "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5" \
  --set-env-vars "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0" \
  --set-env-vars "SPRING_JPA_HIBERNATE_DDL_AUTO=none"

# --- print service URL ---
URL=$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')
log "✅ Deployment zakończony pomyślnie!"
log "🌐 URL usługi: $URL"

# --- usage tips ---
cat <<EOF

Użyteczne komendy:

# Logi na żywo:
gcloud run services logs read $SERVICE --region $REGION --stream

# Lista rewizji:
gcloud run revisions list --service $SERVICE --region $REGION

# Rollback do poprzedniej rewizji:
# gcloud run services update-traffic $SERVICE --region $REGION --to-revisions <REVISION_NAME>=100
EOF