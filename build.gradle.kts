plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.github.spotbugs") version "6.0.26"
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
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.3.1")
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

    // Feign client
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign:4.1.3")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    // Google Cloud SQL support (for production)
    implementation("com.google.cloud.sql:postgres-socket-factory:1.13.1")
    
    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine")
    
    // OpenAPI documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    
    // JSON processing
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-registry-prometheus")
    
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

