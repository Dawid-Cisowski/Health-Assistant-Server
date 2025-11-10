# Quick Start Guide

Get the Health Assistant Event Collector running in 5 minutes!

## Option 1: Docker Compose (Fastest) 🚀

```bash
# 1. Clone and navigate to project
cd /path/to/health-assistant-event-collector

# 2. Start everything with one command
docker-compose up --build

# 3. Wait for startup (~30 seconds), then test
./test-api.sh
```

**That's it!** The API is now running at http://localhost:8080

- 📖 **Swagger UI**: http://localhost:8080/swagger-ui.html
- 🏥 **Health Check**: http://localhost:8080/actuator/health
- 📊 **Metrics**: http://localhost:8080/actuator/prometheus

### Stop the services:
```bash
docker-compose down
```

### Clean up (remove database):
```bash
docker-compose down -v
```

---

## Option 2: Local Development 💻

### Prerequisites
- **Java 21** - [Download](https://adoptium.net/)
- **PostgreSQL 16** - Or use Docker (see below)

### Steps

**1. Start PostgreSQL**

Using Docker:
```bash
docker run --name postgres-dev \
  -e POSTGRES_DB=health_assistant \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:16-alpine
```

Or use your local PostgreSQL installation.

**2. Configure Environment**

```bash
# Copy example env file
cp env.example .env

# Edit .env if needed (optional for defaults)
# Default values work for the Docker Postgres setup above
```

**3. Run the Application**

```bash
# Using Gradle wrapper (no installation needed)
./gradlew bootRun
```

**4. Test the API**

```bash
# In another terminal
./test-api.sh
```

---

## Option 3: IntelliJ IDEA 🧠

1. **Open Project**
   - File → Open → Select `HealthAssistantServer` folder
   - IntelliJ will detect the Gradle project automatically

2. **Start PostgreSQL**
   ```bash
   docker run --name postgres-dev \
     -e POSTGRES_DB=health_assistant \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 \
     -d postgres:16-alpine
   ```

3. **Configure Run Configuration**
   - Click "Add Configuration" → "Spring Boot" → "HealthAssistantApplication"
   - Add environment variables:
     ```
     DB_URL=jdbc:postgresql://localhost:5432/health_assistant
     DB_USER=postgres
     DB_PASSWORD=postgres
     HMAC_DEVICES_JSON={"test-device":"dGVzdC1zZWNyZXQtMTIz"}
     ```

4. **Run** ▶️
   - Click the Run button
   - Wait for "Started HealthAssistantApplication"

5. **Test**
   ```bash
   ./test-api.sh
   ```

---

## Verify Installation ✅

### 1. Check Health
```bash
curl http://localhost:8080/actuator/health
```

Expected output:
```json
{"status":"UP"}
```

### 2. Open Swagger UI
Open in browser: http://localhost:8080/swagger-ui.html

### 3. Test Event Ingestion
```bash
./test-api.sh
```

You should see successful responses with `"status": "stored"`.

---

## Common Issues 🔧

### Port 8080 already in use
```bash
# Option 1: Stop the process using the port
lsof -ti:8080 | xargs kill -9

# Option 2: Change port
export SERVER_PORT=8081
./gradlew bootRun
```

### Port 5432 already in use (PostgreSQL)
```bash
# Option 1: Stop existing PostgreSQL
docker stop postgres-dev

# Option 2: Use different port
docker run --name postgres-dev \
  -e POSTGRES_DB=health_assistant \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5433:5432 \
  -d postgres:16-alpine

# Update DB_URL
export DB_URL=jdbc:postgresql://localhost:5433/health_assistant
```

### Gradle build fails
```bash
# Clean and rebuild
./gradlew clean build

# If still fails, check Java version
java -version  # Should be Java 21
```

### Docker build fails
```bash
# Clear Docker cache
docker system prune -a

# Rebuild
docker-compose build --no-cache
docker-compose up
```

---

## Next Steps 📚

1. **Read API Documentation**
   - Open http://localhost:8080/swagger-ui.html
   - Try the interactive examples

2. **Review Event Types**
   - See `README.md` for all supported event types
   - Check payload schemas in Swagger

3. **Integrate with Mobile App**
   - Generate HMAC secrets: `echo -n "your-secret" | base64`
   - Add device IDs to `HMAC_DEVICES_JSON`
   - Implement HMAC signing in your app (see README.md)

4. **Production Deployment**
   - Use proper secrets management (AWS Secrets Manager, Vault)
   - Enable HTTPS
   - Set up monitoring (Prometheus + Grafana)
   - Configure backups for PostgreSQL

---

## Quick Commands Cheatsheet 📝

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run application
./gradlew bootRun

# Docker: Start
docker-compose up -d

# Docker: Stop
docker-compose down

# Docker: View logs
docker-compose logs -f app

# Test API
./test-api.sh

# Check health
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/prometheus
```

---

**Need help?** Check `README.md` for detailed documentation.

