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

# Get database username from environment or use default
DB_USER=${DB_USER:-"app_user"}

log "Konfiguruję zmienne środowiskowe i sekrety..."
log "  • Profile: production"
log "  • Database: $DB_NAME"
log "  • User: $DB_USER"
log "  • Sekrety: db-password, GEMINI_API_KEY, GOOGLE_FIT_*, HMAC_DEVICES_JSON"

gcloud run deploy "$SERVICE" \
  --image "$FULL_IMAGE" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --port "$PORT" \
  --cpu=1 --memory=512Mi --min-instances=1 --max-instances=10 \
  --add-cloudsql-instances "$INSTANCE_CONN" \
  --set-env-vars "SPRING_PROFILES_ACTIVE=production" \
  --set-env-vars "SPRING_DATASOURCE_URL=jdbc:postgresql:///$DB_NAME?cloudSqlInstance=$INSTANCE_CONN&socketFactory=com.google.cloud.sql.postgres.SocketFactory" \
  --set-env-vars "SPRING_DATASOURCE_USERNAME=$DB_USER" \
  --set-env-vars "HMAC_TOLERANCE_SEC=600" \
  --set-env-vars "NONCE_CACHE_TTL_SEC=600" \
  --set-env-vars "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5" \
  --set-env-vars "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0" \
  --set-env-vars "SPRING_JPA_HIBERNATE_DDL_AUTO=none" \
  --set-secrets "SPRING_DATASOURCE_PASSWORD=db-password:latest" \
  --set-secrets "GEMINI_API_KEY=GEMINI_API_KEY:latest" \
  --set-secrets "GOOGLE_FIT_CLIENT_ID=GOOGLE_FIT_CLIENT_ID:latest" \
  --set-secrets "GOOGLE_FIT_CLIENT_SECRET=GOOGLE_FIT_CLIENT_SECRET:latest" \
  --set-secrets "GOOGLE_FIT_REFRESH_TOKEN=GOOGLE_FIT_REFRESH_TOKEN:latest" \
  --set-secrets "HMAC_DEVICES_JSON=HMAC_DEVICES_JSON:latest"

# --- print service URL ---
URL=$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')
log "✅ Deployment zakończony pomyślnie!"
log "🌐 URL usługi: $URL"

# --- usage tips ---
cat <<EOF

✅ Deployment zakończony!

📝 Wymagane sekrety w Google Cloud Secret Manager:
   • db-password - hasło do bazy danych PostgreSQL
   • GEMINI_API_KEY - klucz API do Google Gemini
   • GOOGLE_FIT_CLIENT_ID - Google Fit OAuth Client ID
   • GOOGLE_FIT_CLIENT_SECRET - Google Fit OAuth Client Secret
   • GOOGLE_FIT_REFRESH_TOKEN - Google Fit OAuth Refresh Token
   • HMAC_DEVICES_JSON - JSON z urządzeniami i ich sekretami HMAC

Tworzenie sekretów (przykład):
echo -n "twoje-haslo" | gcloud secrets create db-password --data-file=-
echo -n "twoj-klucz-api" | gcloud secrets create GEMINI_API_KEY --data-file=-
echo -n '{"device-id":"secret-base64"}' | gcloud secrets create HMAC_DEVICES_JSON --data-file=-

Nadawanie uprawnień Cloud Run do odczytu sekretów:
gcloud secrets add-iam-policy-binding db-password --member="serviceAccount:$PROJECT_ID@serverless-robot-prod.iam.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding GEMINI_API_KEY --member="serviceAccount:$PROJECT_ID@serverless-robot-prod.iam.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding GOOGLE_FIT_CLIENT_ID --member="serviceAccount:$PROJECT_ID@serverless-robot-prod.iam.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding GOOGLE_FIT_CLIENT_SECRET --member="serviceAccount:$PROJECT_ID@serverless-robot-prod.iam.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding GOOGLE_FIT_REFRESH_TOKEN --member="serviceAccount:$PROJECT_ID@serverless-robot-prod.iam.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding HMAC_DEVICES_JSON --member="serviceAccount:$PROJECT_ID@serverless-robot-prod.iam.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"

🔧 Użyteczne komendy:

Logi na żywo:
gcloud run services logs read $SERVICE --region $REGION --stream

Lista rewizji:
gcloud run revisions list --service $SERVICE --region $REGION

Rollback do poprzedniej rewizji:
gcloud run services update-traffic $SERVICE --region $REGION --to-revisions <REVISION_NAME>=100

Lista sekretów:
gcloud secrets list
EOF