# Google Cloud Deployment Guide

This guide explains how to deploy the Health Assistant Event Collector to Google Cloud Run with Cloud SQL.

## Prerequisites

1. **Google Cloud SDK** installed and authenticated:
   ```bash
   gcloud auth login
   gcloud config set project YOUR_PROJECT_ID
   ```

2. **Project setup**:
   - Create a Google Cloud project
   - Enable billing
   - Note your PROJECT_ID

3. **Local configuration**:
   - Update `PROJECT_ID` in `deploy.sh` and `dbdeploy.sh`
   - Set your HMAC devices configuration (optional)

## Deployment Steps

### 1. Database Setup (First Time Only)

Run the database deployment script to create Cloud SQL instance:

```bash
chmod +x dbdeploy.sh
./dbdeploy.sh
```

This will:
- Create a PostgreSQL 16 instance in Cloud SQL
- Create database and user
- Store database password in Secret Manager
- Grant necessary IAM permissions
- Display connection details

**Important**: Save the database password shown in the output!

### 2. Application Deployment

Deploy the application to Cloud Run:

```bash
chmod +x deploy.sh
./deploy.sh
```

This will:
- Build Docker image
- Push to Artifact Registry
- Deploy to Cloud Run with `production` profile
- Connect to Cloud SQL via socket factory
- Set all required environment variables

### 3. Verify Deployment

After successful deployment, test the endpoints:

```bash
# Get service URL
SERVICE_URL=$(gcloud run services describe health-assistant-event-collector \
  --region europe-central2 \
  --format='value(status.url)')

# Test health endpoint
curl $SERVICE_URL/actuator/health

# Test Swagger UI
open $SERVICE_URL/swagger-ui.html
```

## Configuration

### Profiles

The application supports two Spring profiles:

- **`local`** (default): For local development with local PostgreSQL
- **`production`**: For Google Cloud with Cloud SQL socket factory

### Environment Variables

Set via `deploy.sh` or manually in Cloud Run:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `production` |
| `SPRING_DATASOURCE_URL` | JDBC URL with Cloud SQL socket factory | Auto-configured |
| `SPRING_DATASOURCE_USERNAME` | Database username | `health_assistant_user` |
| `SPRING_DATASOURCE_PASSWORD` | Database password (from Secret Manager) | From `db-password` secret |
| `HMAC_DEVICES_JSON` | JSON map of device ID → base64 secret | See `deploy.sh` |
| `HMAC_TOLERANCE_SEC` | HMAC timestamp tolerance in seconds | `600` |
| `NONCE_CACHE_TTL_SEC` | Nonce cache TTL in seconds | `600` |

### Custom HMAC Devices

Before deploying, set your HMAC devices:

```bash
export HMAC_DEVICES_JSON='{"device-id-1":"base64-secret-1","device-id-2":"base64-secret-2"}'
./deploy.sh
```

## Management

### View Logs

```bash
# Stream logs in real-time
gcloud run services logs read health-assistant-event-collector \
  --region europe-central2 \
  --stream

# View recent logs
gcloud run services logs read health-assistant-event-collector \
  --region europe-central2 \
  --limit 100
```

### Update Environment Variables

```bash
gcloud run services update health-assistant-event-collector \
  --region europe-central2 \
  --set-env-vars "HMAC_TOLERANCE_SEC=900"
```

### Rollback Deployment

```bash
# List revisions
gcloud run revisions list \
  --service health-assistant-event-collector \
  --region europe-central2

# Rollback to specific revision
gcloud run services update-traffic health-assistant-event-collector \
  --region europe-central2 \
  --to-revisions REVISION_NAME=100
```

### Database Management

#### Connect to Cloud SQL

```bash
# Using Cloud SQL proxy
gcloud sql connect app-db \
  --user=app_user \
  --database=app
```

#### Run Flyway Migrations

Migrations run automatically on application startup. To run manually:

```bash
# SSH into a Cloud Run instance
gcloud run services proxy health-assistant-event-collector \
  --region europe-central2
```

#### Backup Database

```bash
# Create on-demand backup
gcloud sql backups create \
  --instance app-db

# List backups
gcloud sql backups list \
  --instance app-db
```

## Cost Optimization

### Cloud Run
- **Min instances**: 0 (scale to zero when idle)
- **Max instances**: 10
- **CPU**: 1 vCPU
- **Memory**: 512 MiB

Estimated cost: **~$5-20/month** (depending on traffic)

### Cloud SQL
- **CPU**: 1 vCPU
- **Memory**: 3.75 GB
- **Storage**: 20 GB SSD
- **Availability**: Zonal

Estimated cost: **~$50-70/month**

### Total estimated cost: **~$55-90/month**

To reduce costs:
- Use smaller Cloud SQL instance during development
- Enable automatic instance scaling down
- Use preemptible instances for non-production

## Troubleshooting

### Application won't start

```bash
# Check logs
gcloud run services logs read health-assistant-event-collector \
  --region europe-central2 \
  --limit 50

# Common issues:
# 1. Database password incorrect → Check Secret Manager
# 2. Cloud SQL connection failed → Verify IAM permissions
# 3. Flyway migration failed → Check database state
```

### Database connection issues

```bash
# Verify Cloud SQL instance is running
gcloud sql instances describe app-db

# Check IAM bindings
gcloud projects get-iam-policy health-assistant-477621 \
  --flatten="bindings[].members" \
  --filter="bindings.role:roles/cloudsql.client"
```

### Update database password

```bash
# Generate new password
NEW_PASS=$(openssl rand -base64 32)

# Update Cloud SQL user
gcloud sql users set-password app_user \
  --instance app-db \
  --password "$NEW_PASS"

# Update Secret Manager
echo -n "$NEW_PASS" | gcloud secrets versions add db-password --data-file=-

# Redeploy service to pick up new secret
./deploy.sh
```

## Security Best Practices

1. **Secrets Management**:
   - Never commit credentials to Git
   - Use Secret Manager for sensitive data
   - Rotate passwords regularly

2. **Network Security**:
   - Cloud SQL uses private IP (no public exposure)
   - Cloud Run connects via Unix socket
   - Enable VPC connector for production

3. **Authentication**:
   - Use HMAC authentication for all API calls
   - Rotate HMAC secrets regularly
   - Monitor for replay attacks via logs

4. **Monitoring**:
   - Set up Cloud Monitoring alerts
   - Monitor error rates and latency
   - Review Cloud Audit Logs

## CI/CD Integration

Example GitHub Actions workflow:

```yaml
name: Deploy to Cloud Run

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - uses: google-github-actions/auth@v1
        with:
          credentials_json: ${{ secrets.GCP_SA_KEY }}
      
      - name: Deploy
        run: |
          export PROJECT_ID=health-assistant-477621
          export HMAC_DEVICES_JSON='${{ secrets.HMAC_DEVICES_JSON }}'
          ./deploy.sh
```

## Support

For issues or questions:
- Check [main README](README.md) for application details
- Review [test documentation](integration-tests/README.md)
- Check Google Cloud documentation

