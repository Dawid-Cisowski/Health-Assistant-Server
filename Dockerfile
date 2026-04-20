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

# --- CDS training stage ---
# Generuje Class Data Sharing archive z załadowanymi klasami Spring/JVM
# Redukuje cold start z ~15-20s do ~3-5s
FROM eclipse-temurin:21-jre AS cds
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar

# Training run: Spring ładuje kontekst i kończy po refresh.
# || true — brak DB w buildzie powoduje błąd, ale archiwum JVM/Spring klas
# i tak zostaje zapisane przez -XX:ArchiveClassesAtExit przy wyjściu JVM.
RUN java -Dspring.context.exit=onRefresh \
    -XX:ArchiveClassesAtExit=/app/app.jsa \
    -jar /app/app.jar || true

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar /app/app.jar
COPY --from=cds /app/app.jsa /app/app.jsa

# Cloud Run ustawia PORT — Spring musi go użyć
ENV PORT=8080

# Profil produkcyjny (może być nadpisany przez Cloud Run env vars)
ENV SPRING_PROFILES_ACTIVE=production

RUN useradd --no-create-home --shell /bin/false appuser
USER appuser
EXPOSE 8080

# Uwaga: HEALTHCHECK z Dockera jest ignorowany w Cloud Run
# Zamiast tego włącz Spring Actuator i ustaw /actuator/health jako probe w serwisie.

ENTRYPOINT ["java", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dserver.port=${PORT}", \
  "-jar", "/app/app.jar"]