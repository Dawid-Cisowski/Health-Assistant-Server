# Deployment Guide

Complete guide for deploying the Health Assistant Event Collector to production.

## Prerequisites

- Java 21 runtime
- PostgreSQL 16+
- Docker (for containerized deployment)
- Container orchestration (Kubernetes, ECS, Cloud Run)

## Deployment Options

### 1. Docker Compose (Development/Staging)

**Best for:** Local development, small staging environments

```bash
# 1. Create production docker-compose.yml
cat > docker-compose.prod.yml <<EOF
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: health_assistant
      POSTGRES_USER: \${DB_USER}
      POSTGRES_PASSWORD: \${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

  app:
    image: health-assistant-event-collector:latest
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/health_assistant
      DB_USER: \${DB_USER}
      DB_PASSWORD: \${DB_PASSWORD}
      HMAC_DEVICES_JSON: \${HMAC_DEVICES_JSON}
      HMAC_TOLERANCE_SEC: 300
      NONCE_CACHE_TTL_SEC: 300
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    restart: unless-stopped

volumes:
  postgres_data:
EOF

# 2. Build image
docker build -t health-assistant-event-collector:latest .

# 3. Create .env file with secrets
cat > .env <<EOF
DB_USER=prod_user
DB_PASSWORD=CHANGE_ME
HMAC_DEVICES_JSON={"device1":"<base64-secret>"}
EOF

# 4. Deploy
docker-compose -f docker-compose.prod.yml up -d
```

### 2. Kubernetes

**Best for:** Production, scalable deployments

#### ConfigMap & Secrets

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: health-assistant-config
data:
  DB_URL: "jdbc:postgresql://postgres-service:5432/health_assistant"
  HMAC_TOLERANCE_SEC: "300"
  NONCE_CACHE_TTL_SEC: "300"

---
# secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: health-assistant-secrets
type: Opaque
stringData:
  DB_USER: "postgres"
  DB_PASSWORD: "CHANGE_ME"
  HMAC_DEVICES_JSON: '{"device1":"base64secret"}'
```

#### Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: health-assistant
spec:
  replicas: 3
  selector:
    matchLabels:
      app: health-assistant
  template:
    metadata:
      labels:
        app: health-assistant
    spec:
      containers:
      - name: app
        image: health-assistant-event-collector:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          valueFrom:
            configMapKeyRef:
              name: health-assistant-config
              key: DB_URL
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: health-assistant-secrets
              key: DB_USER
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: health-assistant-secrets
              key: DB_PASSWORD
        - name: HMAC_DEVICES_JSON
          valueFrom:
            secretKeyRef:
              name: health-assistant-secrets
              key: HMAC_DEVICES_JSON
        - name: HMAC_TOLERANCE_SEC
          valueFrom:
            configMapKeyRef:
              name: health-assistant-config
              key: HMAC_TOLERANCE_SEC
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5

---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: health-assistant-service
spec:
  selector:
    app: health-assistant
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

#### Deploy

```bash
# Apply configurations
kubectl apply -f configmap.yaml
kubectl apply -f secrets.yaml
kubectl apply -f deployment.yaml

# Check status
kubectl get pods
kubectl get svc

# View logs
kubectl logs -f deployment/health-assistant
```

### 3. AWS ECS (Fargate)

**Best for:** AWS-native deployments

#### Task Definition

```json
{
  "family": "health-assistant",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "health-assistant-app",
      "image": "<account-id>.dkr.ecr.<region>.amazonaws.com/health-assistant:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "HMAC_TOLERANCE_SEC",
          "value": "300"
        }
      ],
      "secrets": [
        {
          "name": "DB_URL",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-url"
        },
        {
          "name": "DB_USER",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-user"
        },
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-password"
        },
        {
          "name": "HMAC_DEVICES_JSON",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:hmac-devices"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/health-assistant",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": [
          "CMD-SHELL",
          "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"
        ],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      }
    }
  ]
}
```

#### Deploy Script

```bash
#!/bin/bash

# Build and push image
docker build -t health-assistant:latest .
docker tag health-assistant:latest <account>.dkr.ecr.<region>.amazonaws.com/health-assistant:latest
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
docker push <account>.dkr.ecr.<region>.amazonaws.com/health-assistant:latest

# Register task definition
aws ecs register-task-definition --cli-input-json file://task-definition.json

# Update service
aws ecs update-service \
  --cluster health-assistant-cluster \
  --service health-assistant-service \
  --task-definition health-assistant
```

### 4. Google Cloud Run

**Best for:** Serverless, auto-scaling

```bash
# 1. Build and push to GCR
gcloud builds submit --tag gcr.io/PROJECT_ID/health-assistant

# 2. Deploy
gcloud run deploy health-assistant \
  --image gcr.io/PROJECT_ID/health-assistant \
  --platform managed \
  --region us-central1 \
  --set-env-vars "HMAC_TOLERANCE_SEC=300" \
  --set-secrets "DB_URL=db-url:latest,DB_USER=db-user:latest,DB_PASSWORD=db-password:latest,HMAC_DEVICES_JSON=hmac-devices:latest" \
  --allow-unauthenticated \
  --port 8080 \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 1 \
  --max-instances 10
```

## Database Setup

### Managed PostgreSQL

**AWS RDS:**
```bash
aws rds create-db-instance \
  --db-instance-identifier health-assistant-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 16.1 \
  --master-username postgres \
  --master-user-password CHANGE_ME \
  --allocated-storage 20 \
  --backup-retention-period 7 \
  --publicly-accessible false
```

**Google Cloud SQL:**
```bash
gcloud sql instances create health-assistant-db \
  --database-version=POSTGRES_16 \
  --tier=db-f1-micro \
  --region=us-central1
```

**Azure Database for PostgreSQL:**
```bash
az postgres flexible-server create \
  --name health-assistant-db \
  --resource-group myResourceGroup \
  --location eastus \
  --admin-user postgres \
  --admin-password CHANGE_ME \
  --sku-name Standard_B1ms \
  --version 16
```

### Migration

Flyway runs automatically on startup. For manual migration:

```bash
# Using Gradle
./gradlew flywayMigrate -Pflyway.url=jdbc:postgresql://host:5432/db -Pflyway.user=user -Pflyway.password=pass

# Using Flyway CLI
flyway migrate \
  -url=jdbc:postgresql://host:5432/health_assistant \
  -user=postgres \
  -password=CHANGE_ME \
  -locations=filesystem:src/main/resources/db/migration
```

## Security Checklist

- [ ] **HTTPS/TLS enabled** (use reverse proxy: nginx, ALB, Ingress)
- [ ] **Secrets in secure storage** (AWS Secrets Manager, HashiCorp Vault)
- [ ] **Database encryption at rest** (enable in cloud provider)
- [ ] **Database encryption in transit** (SSL/TLS connections)
- [ ] **Network isolation** (VPC, private subnets)
- [ ] **Strong HMAC secrets** (256-bit random, rotated regularly)
- [ ] **Firewall rules** (allow only necessary ports)
- [ ] **Rate limiting** (use API Gateway or nginx)
- [ ] **DDoS protection** (CloudFlare, AWS Shield)
- [ ] **Regular security updates** (OS, Java, dependencies)

## Monitoring Setup

### Prometheus + Grafana

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'health-assistant'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['health-assistant:8080']
```

### CloudWatch (AWS)

```bash
# Install CloudWatch agent
aws cloudwatch put-metric-data \
  --namespace HealthAssistant \
  --metric-name EventsIngested \
  --value 1
```

### Custom Dashboards

**Key Metrics:**
- Request rate (RPS)
- Request latency (P50, P95, P99)
- Error rate (4xx, 5xx)
- Database connection pool usage
- JVM memory usage
- Event ingestion rate

## Backup Strategy

### Database Backups

**Automated (RDS):**
- Enable automated backups (7-30 days retention)
- Configure backup window
- Enable point-in-time recovery

**Manual:**
```bash
# PostgreSQL dump
pg_dump -h hostname -U username -d health_assistant -F c -b -v -f backup.dump

# Restore
pg_restore -h hostname -U username -d health_assistant -v backup.dump
```

### Disaster Recovery

1. **Database replication** - Multi-AZ deployment
2. **Cross-region backups** - For major disasters
3. **Regular restore testing** - Verify backups work
4. **RTO/RPO targets** - Define recovery objectives

## Scaling Considerations

### Horizontal Scaling

- Stateless application (safe to scale)
- Use load balancer
- Increase replicas based on CPU/memory

```bash
# Kubernetes
kubectl scale deployment health-assistant --replicas=5

# ECS
aws ecs update-service --service health-assistant --desired-count 5
```

### Database Scaling

- **Read replicas** - For read-heavy workloads (future)
- **Connection pooling** - Adjust HikariCP settings
- **Vertical scaling** - Increase DB instance size

### Cache Optimization

```yaml
# Increase nonce cache size
NONCE_CACHE_TTL_SEC: 600
```

## CI/CD Pipeline

### GitHub Actions Example

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build
        run: ./gradlew build
      
      - name: Build Docker image
        run: docker build -t health-assistant:${{ github.sha }} .
      
      - name: Push to registry
        run: |
          docker tag health-assistant:${{ github.sha }} registry.example.com/health-assistant:${{ github.sha }}
          docker push registry.example.com/health-assistant:${{ github.sha }}
      
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/health-assistant \
            app=registry.example.com/health-assistant:${{ github.sha }}
```

## Rollback Strategy

```bash
# Kubernetes
kubectl rollout undo deployment/health-assistant

# ECS
aws ecs update-service \
  --cluster health-assistant-cluster \
  --service health-assistant-service \
  --task-definition health-assistant:PREVIOUS_VERSION

# Docker Compose
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d --scale app=0
# Fix issue, then scale back up
docker-compose -f docker-compose.prod.yml up -d --scale app=2
```

## Cost Optimization

1. **Right-size resources** - Don't over-provision
2. **Auto-scaling** - Scale down during low traffic
3. **Reserved instances** - For predictable workloads
4. **Database optimization** - Use appropriate tier
5. **CloudFront/CDN** - Cache static content (if applicable)

## Summary Checklist

Before going to production:

- [ ] Build succeeds: `./gradlew build`
- [ ] Tests pass: `./gradlew test`
- [ ] Docker image builds: `docker build -t app .`
- [ ] Database migration tested
- [ ] Secrets configured in secret manager
- [ ] HTTPS/TLS configured
- [ ] Monitoring configured
- [ ] Backup strategy in place
- [ ] Scaling limits set
- [ ] Documentation updated
- [ ] Runbook created for operations team

---

**Production ready!** 🚀

