#!/usr/bin/env bash
# Database deployment script: creates Cloud SQL instance and configures Cloud Run access
# Run this BEFORE running deploy.sh for the first time

set -euo pipefail

# === CONFIGURATION ===
export PROJECT_ID=${PROJECT_ID:-"health-assistant-477621"}
export REGION=${REGION:-"europe-central2"}
export INSTANCE=${INSTANCE:-"app-db"}
export DB_NAME=${DB_NAME:-"app"}
export DB_USER=${DB_USER:-"app_user"}
export DB_PASS=${DB_PASS:-"$(openssl rand -base64 32)"}  # Generate random password
export SERVICE=${SERVICE:-"health-assistant-event-collector"}

# === HELPER ===
log() { echo -e "\033[1;35m[dbdeploy]\033[0m $*"; }

log "Projekt: $PROJECT_ID  | Region: $REGION  | Instance: $INSTANCE"
log "⚠️  UWAGA: To utworzy Cloud SQL instance (kosztowne). Kontynuować? (y/n)"
read -r response
if [[ ! "$response" =~ ^[Yy]$ ]]; then
  log "Anulowano."
  exit 0
fi

# API
gcloud config set project "$PROJECT_ID"
gcloud services enable sqladmin.googleapis.com secretmanager.googleapis.com run.googleapis.com

# --- create Cloud SQL instance (PostgreSQL 16) ---
log "Tworzę Cloud SQL instance..."
gcloud sql instances create "$INSTANCE" \
  --database-version=POSTGRES_16 \
  --cpu=1 --memory=3840MiB \
  --region="$REGION" \
  --availability-type=zonal \
  --storage-type=SSD --storage-size=20GB \
  --backup-start-time=03:00 || log "Instance już istnieje (lub błąd)"

# --- create database and user ---
log "Tworzę bazę danych i użytkownika..."
gcloud sql databases create "$DB_NAME" --instance "$INSTANCE" || log "Baza już istnieje"
gcloud sql users create "$DB_USER" --instance "$INSTANCE" --password "$DB_PASS" || log "Użytkownik już istnieje"

# --- create secret for database password ---
log "Tworzę secret dla hasła do bazy danych..."
echo -n "$DB_PASS" | gcloud secrets create db-password --replication-policy=automatic --data-file=- || {
  log "Secret już istnieje, aktualizuję wersję..."
  echo -n "$DB_PASS" | gcloud secrets versions add db-password --data-file=-
}

log "✅ Hasło bazy danych: $DB_PASS"
log "   (zapisz je w bezpiecznym miejscu!)"

# --- setup service account permissions ---
log "Konfiguruję uprawnienia..."
export PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
export SA_EMAIL="$PROJECT_NUMBER-compute@developer.gserviceaccount.com"

log "Service Account: $SA_EMAIL"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/cloudsql.client" || log "Uprawnienie już nadane"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/secretmanager.secretAccessor" || log "Uprawnienie już nadane"

# --- get connection name ---
export INSTANCE_CONN=$(gcloud sql instances describe "$INSTANCE" --format='value(connectionName)')
log "✅ Cloud SQL Connection Name: $INSTANCE_CONN"

# --- summary ---
cat <<EOF

✅ Konfiguracja bazy danych zakończona!

Podsumowanie:
  • Instance: $INSTANCE
  • Connection: $INSTANCE_CONN
  • Database: $DB_NAME
  • User: $DB_USER
  • Password: $DB_PASS (zapisz w bezpiecznym miejscu!)

Następne kroki:
1. Uruchom deploy aplikacji:
   ./deploy.sh

2. Sprawdź logi usługi:
   gcloud run services logs read $SERVICE --region $REGION --stream

3. Aby połączyć się lokalnie (proxy):
   gcloud sql connect $INSTANCE --user=$DB_USER --database=$DB_NAME
EOF