package com.healthassistant.service;

import com.healthassistant.dto.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventValidator
 */
class EventValidatorTest {

    private EventValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EventValidator();
    }

    @Test
    void testValidateStepsBucketed_Valid() {
        EventEnvelope envelope = EventEnvelope.builder()
            .idempotencyKey("test-key")
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "count", 742,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        List<String> errors = validator.validate(envelope);
        assertThat(errors).isEmpty();
    }

    @Test
    void testValidateStepsBucketed_MissingField() {
        EventEnvelope envelope = EventEnvelope.builder()
            .idempotencyKey("test-key")
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "originPackage", "com.heytap.health.international"
                // missing count
            ))
            .build();

        List<String> errors = validator.validate(envelope);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("count");
    }

    @Test
    void testValidateStepsBucketed_NegativeCount() {
        EventEnvelope envelope = EventEnvelope.builder()
            .idempotencyKey("test-key")
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "count", -10,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        List<String> errors = validator.validate(envelope);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("count") && e.contains("non-negative"));
    }

    @Test
    void testValidateHeartRateSummary_Valid() {
        EventEnvelope envelope = EventEnvelope.builder()
            .idempotencyKey("test-key")
            .type("HeartRateSummaryRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T07:00:00Z",
                "bucketEnd", "2025-11-09T07:15:00Z",
                "avg", 78.3,
                "min", 61,
                "max", 115,
                "samples", 46,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        List<String> errors = validator.validate(envelope);
        assertThat(errors).isEmpty();
    }

    @Test
    void testValidateInvalidEventType() {
        EventEnvelope envelope = EventEnvelope.builder()
            .idempotencyKey("test-key")
            .type("InvalidType.v1")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .build();

        List<String> errors = validator.validate(envelope);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("Invalid event type");
    }

    @Test
    void testValidateMealLogged_Valid() {
        EventEnvelope envelope = EventEnvelope.builder()
            .idempotencyKey("test-key")
            .type("MealLoggedEstimated.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "when", "2025-11-09T12:00:00Z",
                "items", List.of(
                    Map.of(
                        "name", "Chicken Breast",
                        "portion", "200g",
                        "kcal", 330,
                        "protein_g", 62,
                        "carbs_g", 0,
                        "fat_g", 7.2
                    )
                ),
                "total", Map.of(
                    "kcal", 330,
                    "protein_g", 62,
                    "carbs_g", 0,
                    "fat_g", 7.2
                )
            ))
            .build();

        List<String> errors = validator.validate(envelope);
        assertThat(errors).isEmpty();
    }
}

