import java.time.Duration

plugins {
    groovy
    jacoco
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.healthassistant"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
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
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.3.1")
    }
}

dependencies {
    // Reference to main application (including all its dependencies)
    testImplementation(project(":"))
    testRuntimeOnly(project(":"))

    // Spring Boot Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // Need explicit dependency for compilation
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Spring AI for ChatModel mock and evaluation tests
    testImplementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    testCompileOnly("io.projectreactor:reactor-core")

    // Testcontainers
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:spock:1.19.3")
    
    // Spock Framework (Groovy testing)
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    testImplementation("org.spockframework:spock-spring:2.3-groovy-4.0")
    testImplementation("org.apache.groovy:groovy:4.0.15")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // REST Assured for API testing
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")

    // Awaitility for async testing
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.awaitility:awaitility-groovy:4.2.0")
    
    // WireMock for mocking external APIs
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Don't create a bootJar for this module (it's tests only)
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

// JaCoCo configuration
jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    ignoreFailures = true  // Allow coverage report even when tests fail
    finalizedBy(tasks.jacocoTestReport)

    // Exclude evaluation tests from regular test run (they require real Gemini API)
    exclude("**/*HallucinationSpec.class", "**/*EvaluationSpec.class")
}

// Separate task for AI evaluation tests using real Gemini API
tasks.register<Test>("evaluationTest") {
    description = "Run AI evaluation tests with real Gemini API (requires GEMINI_API_KEY)"
    group = "verification"

    useJUnitPlatform()

    // Only run evaluation specs
    include("**/*HallucinationSpec.class", "**/*EvaluationSpec.class")

    // Set profiles - evaluation profile uses real Gemini, not mock
    systemProperty("spring.profiles.active", "test,evaluation")

    // Pass through GEMINI_API_KEY from environment
    environment("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY") ?: "")

    // Longer timeout for LLM inference
    timeout.set(Duration.ofMinutes(10))
}

tasks.jacocoTestReport {
    // Include main module classes in coverage report
    classDirectories.setFrom(
        files(
            project(":").layout.buildDirectory.dir("classes/java/main")
        )
    )
    sourceDirectories.setFrom(
        files(
            project(":").file("src/main/java")
        )
    )

    reports {
        xml.required = true
        html.required = true
    }
}

