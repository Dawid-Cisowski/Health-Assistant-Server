plugins {
    java
    groovy
    jacoco
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.0.26"
    id("org.sonarqube") version "5.1.0.4882"
    pmd
}

group = "com.healthassistant"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.google.com")
    }
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.1")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Log4j2 with JSON logging
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("com.lmax:disruptor:3.4.4") // Async logging support
    implementation("org.apache.logging.log4j:log4j-layout-template-json:2.23.1")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")

    // Feign client (version managed by Spring Cloud BOM)
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-admin:9.4.3")

    // Database
    implementation("org.postgresql:postgresql:42.7.4")
    // Spring Boot 4.0 requires starter for Flyway autoconfiguration
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Google Cloud SQL support (for production)
    implementation("com.google.cloud.sql:postgres-socket-factory:1.13.1")

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine")

    // OpenAPI documentation (Spring Boot 4.0 compatible)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0-M1")

    // JSON processing
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Micrometer Stackdriver for Google Cloud Monitoring
    implementation("io.micrometer:micrometer-registry-stackdriver") {
        exclude(group = "io.grpc", module = "grpc-xds")
    }
    // gRPC dependencies - aligned to 1.70.0 to match google-genai transitive deps
    implementation("io.grpc:grpc-netty-shaded:1.70.0")

    // Lombok for reducing boilerplate
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // MapStruct for object mapping
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation("org.springframework.modulith:spring-modulith-actuator")
    implementation("org.springframework.modulith:spring-modulith-events-jackson")

    // Testing (basic unit test support only)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.spockframework:spock-core:2.4-groovy-5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// SpotBugs configuration
spotbugs {
    ignoreFailures = false
    showStackTraces = true
    showProgress = true
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.LOW
    excludeFilter = file("config/spotbugs/exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}

// Exclude Groovy test classes from SpotBugs and PMD (they don't analyze Groovy correctly)
tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") {
    classes = classes?.filter { !it.path.contains("/groovy/") }
}

tasks.named<Pmd>("pmdTest") {
    source = source.matching { exclude("**/*.groovy") }
}

// PMD configuration
pmd {
    isConsoleOutput = true
    toolVersion = "7.8.0"
    rulesMinimumPriority = 5
    ruleSets = emptyList()  // Clear default rulesets
    ruleSetFiles = files("config/pmd/ruleset.xml")
}

// JaCoCo configuration
jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// SonarQube configuration
sonar {
    properties {
        property("sonar.projectKey", "health-assistant-server")
        property("sonar.host.url", "https://sonarcloud.io")

        // JaCoCo coverage from integration-tests module
        property("sonar.coverage.jacoco.xmlReportPaths",
            "${project(":integration-tests").layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")

        // Source configuration
        property("sonar.sources", "src/main/java")
        property("sonar.java.binaries", "${layout.buildDirectory.get()}/classes/java/main")
    }
}

