# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Kopiujemy tylko pliki builda, żeby zcache'ować zależności
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

# Pobierz zależności (cache)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Dopiero teraz źródła
COPY src src

# Build bez testów
RUN ./gradlew bootJar --no-daemon -x test

# --- runtime stage (distroless) ---
FROM gcr.io/distroless/java21
WORKDIR /app

# JAR — dopasuj pattern jeśli masz inną nazwę
COPY --from=build /app/build/libs/*.jar /app/app.jar

# Cloud Run ustawia PORT — Spring musi go użyć
ENV PORT=8080
USER nonroot
EXPOSE 8080

# Uwaga: HEALTHCHECK z Dockera jest ignorowany w Cloud Run
# Zamiast tego włącz Spring Actuator i ustaw /actuator/health jako probe w serwisie.

ENTRYPOINT ["java",
  "-XX:MaxRAMPercentage=75.0",
  "-Djava.security.egd=file:/dev/./urandom",
  "-Dserver.port=${PORT}",
  "-jar","/app/app.jar"
]