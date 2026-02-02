package com.healthassistant.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenTelemetry OTLP metrics export.
 * Adds common tags to all metrics for consistent labeling in Google Cloud Monitoring.
 */
@Configuration
class OtlpMetricsConfiguration {

    private static final String APPLICATION_NAME = "health-assistant-server";

    @Value("${APP_VERSION:1.0.0}")
    private String applicationVersion;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config()
                .commonTags(List.of(
                        Tag.of("application", APPLICATION_NAME),
                        Tag.of("version", applicationVersion),
                        Tag.of("environment", activeProfile)
                ));
    }
}
