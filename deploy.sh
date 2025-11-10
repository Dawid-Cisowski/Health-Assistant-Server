#!/usr/bin/env bash
# Deployment script: builds Docker image and deploys to Cloud Run
# Compatible with macOS + Google Cloud SDK

set -euo pipefail

# === CONFIGURATION ===
PROJECT_ID=${PROJECT_ID:-"health-assistant-477621}     # <-- wpisz swój project ID
REGION=${REGION:-"europe-central2"}                    # region (Warszawa)
REPO=${REPO:-"health-assistant-event-collector"}       # nazwa repozytorium w Artifact Registry
IMAGE=${IMAGE:-"health-assistant-event-collector"}     # nazwa obrazu
SERVICE=${SERVICE:-"health-assistant-event-collector"} # nazwa usługi Cloud Run
PORT=${PORT:-8080}                                     # port aplikacji
TAG=$(date +%Y%m%d-%H%M%S)                             # tag oparty na dacie

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

# --- deploy to Cloud Run ---
log "Wdrażam usługę do Cloud Run..."
gcloud run deploy "$SERVICE" \
  --image "$FULL_IMAGE" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --port "$PORT" \
  --cpu=1 --memory=512Mi --min-instances=0

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