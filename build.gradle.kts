plugins {
    java
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
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

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

    // Testing (basic unit test support only)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

