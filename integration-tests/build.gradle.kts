import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

plugins {
    groovy
    jacoco
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
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
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.1")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
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

    // Spring Modulith events core for serialization tests
    testImplementation("org.springframework.modulith:spring-modulith-events-core")

    // Testcontainers (versions managed by BOM)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:spock")

    // Spock Framework (Groovy testing) - Groovy 5.0 for Spring Boot 4.0 compatibility
    testImplementation("org.spockframework:spock-core:2.4-groovy-5.0")
    testImplementation("org.spockframework:spock-spring:2.4-groovy-5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // REST Assured for API testing (6.0.0+ for Groovy 5 support)
    testImplementation("io.rest-assured:rest-assured:6.0.0")
    testImplementation("io.rest-assured:json-path:6.0.0")

    // Awaitility for async testing (exclude old Groovy package for Spring Boot 4.0 compatibility)
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.awaitility:awaitility-groovy:4.2.0") {
        exclude(group = "org.codehaus.groovy", module = "groovy")
    }

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

    // Exclude AI evaluation and benchmark tests from regular test run (they require real Gemini API)
    exclude("**/evaluation/**")
    exclude("**/benchmark/**")

    // Enable parallel test execution (use half of available cores, minimum 1)
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // JVM args for parallel execution
    jvmArgs("-Xmx1g", "-XX:+UseParallelGC")

    // Ensure tests have enough time
    timeout.set(Duration.ofMinutes(15))
}

// Separate task for AI evaluation tests using real Gemini API
tasks.register<Test>("evaluationTest") {
    description = "Run AI evaluation tests with real Gemini API (requires GEMINI_API_KEY)"
    group = "verification"

    useJUnitPlatform()

    // Only run evaluation tests from evaluation package (exclude benchmark)
    include("**/evaluation/**")
    exclude("**/benchmark/**")

    // Set profiles - evaluation profile uses real Gemini, not mock
    systemProperty("spring.profiles.active", "test,evaluation")

    // Pass through GEMINI_API_KEY from environment
    environment("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY") ?: "")

    // Longer timeout for LLM inference
    timeout.set(Duration.ofMinutes(15))

    // Enable parallel test execution (limited to avoid Gemini API rate limiting)
    maxParallelForks = 10

    // JVM args for parallel execution
    jvmArgs("-Xmx1g", "-XX:+UseParallelGC")

    // Show test output and exceptions in console
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Separate task for AI benchmark tests (quality, cost, time metrics)
// Usage:
//   Single model:  GEMINI_API_KEY=key ./gradlew :integration-tests:benchmarkTest
//   Multi-model:   GEMINI_API_KEY=key BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05" ./gradlew :integration-tests:benchmarkTest
tasks.register<Test>("benchmarkTest") {
    description = "Run AI benchmark tests measuring quality, cost, and latency (requires GEMINI_API_KEY)"
    group = "verification"

    useJUnitPlatform()

    // Only run benchmark tests
    include("**/benchmark/**")

    // Set profiles - evaluation profile uses real Gemini, not mock
    systemProperty("spring.profiles.active", "test,evaluation")

    // Pass through environment variables
    // GEMINI_API_KEY - required for API access
    // BENCHMARK_MODELS - comma-separated list of models to compare (e.g., "gemini-2.0-flash,gemini-2.0-pro-exp-02-05")
    // GEMINI_MODEL - fallback single model if BENCHMARK_MODELS not set
    environment("GEMINI_API_KEY", System.getenv("GEMINI_API_KEY") ?: "")
    environment("BENCHMARK_MODELS", System.getenv("BENCHMARK_MODELS") ?: "")
    environment("GEMINI_MODEL", System.getenv("GEMINI_MODEL") ?: "gemini-2.0-flash")

    // Longer timeout for LLM inference
    timeout.set(Duration.ofMinutes(15))

    // Enable parallel test execution
    maxParallelForks = 10

    // JVM args
    jvmArgs("-Xmx1g", "-XX:+UseParallelGC")

    // Output benchmark report directory
    outputs.dir(layout.buildDirectory.dir("reports/benchmark"))

    // Show test output and exceptions in console
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    doLast {
        println("")
        println("Benchmark reports:")
        println("  JSON: ${layout.buildDirectory.get()}/reports/benchmark/benchmark-report.json")
        println("  HTML: ${layout.buildDirectory.get()}/reports/benchmark/benchmark-report.html")
    }
}

// ===========================================================================
// Test Summary — prints a pass/fail table after each test run
// Failures are sorted to the top. For evaluationTest (which uses @Retry),
// failed tests are annotated with [retried] so you know all attempts failed.
// ===========================================================================

fun printTestSummary(taskName: String, entries: List<Array<String>>) {
    if (entries.isEmpty()) return
    // entries: [spec, test, status, note]  where status = PASS | FAIL | SKIP
    val sorted = entries.sortedWith(compareBy({ it[2] != "FAIL" }, { it[0] }, { it[1] }))
    val passed = entries.count { it[2] == "PASS" }
    val failed = entries.count { it[2] == "FAIL" }
    val skipped = entries.count { it[2] == "SKIP" }
    val specW = sorted.maxOfOrNull { it[0].length }?.coerceIn(4, 40) ?: 4
    val testW = sorted.maxOfOrNull { it[1].length }?.coerceIn(4, 60) ?: 4
    val hasNotes = sorted.any { it[3].isNotEmpty() }
    val noteW = if (hasNotes) 12 else 0
    val width = specW + testW + 14 + noteW
    val bar = "=".repeat(width)
    val sep = "-".repeat(width)
    println("")
    println(bar)
    println(" TEST SUMMARY [$taskName]  |  Total: ${entries.size}  PASS: $passed  FAIL: $failed  SKIP: $skipped")
    println(bar)
    val noteHeader = if (hasNotes) "  ${"NOTE".padEnd(noteW)}" else ""
    println(" ${"SPEC".padEnd(specW)}  ${"TEST".padEnd(testW)}  STATUS$noteHeader")
    println(sep)
    sorted.forEach { e ->
        val noteCol = when {
            e[3].isNotEmpty() -> "  ${e[3].take(noteW).padEnd(noteW)}"
            hasNotes -> "  ${" ".repeat(noteW)}"
            else -> ""
        }
        val suffix = if (e[2] == "FAIL") " <--" else ""
        println(" ${e[0].take(specW).padEnd(specW)}  ${e[1].take(testW).padEnd(testW)}  ${e[2]}$noteCol$suffix")
    }
    println(bar)
    println("")
}

tasks.named<Test>("evaluationTest") {
    val entries = CopyOnWriteArrayList<Array<String>>()
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
        override fun beforeTest(descriptor: TestDescriptor) {}
        override fun afterTest(descriptor: TestDescriptor, result: TestResult) {
            val spec = descriptor.className?.substringAfterLast('.') ?: "Unknown"
            val status = when (result.resultType) {
                TestResult.ResultType.SUCCESS -> "PASS"
                TestResult.ResultType.FAILURE -> "FAIL"
                else -> "SKIP"
            }
            val note = if (result.resultType == TestResult.ResultType.FAILURE) "[retried]" else ""
            entries.add(arrayOf(spec, descriptor.displayName, status, note))
        }
    })
    doLast {
        printTestSummary("evaluationTest", entries.toList())
    }
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

