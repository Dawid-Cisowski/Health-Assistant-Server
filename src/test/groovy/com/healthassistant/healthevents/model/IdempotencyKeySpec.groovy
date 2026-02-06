package com.healthassistant.healthevents.model

import com.healthassistant.healthevents.api.model.IdempotencyKey
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload
import com.healthassistant.healthevents.api.dto.payload.StepsPayload
import spock.lang.Specification
import spock.lang.Title

import java.time.Instant

@Title("IdempotencyKey - key generation and validation")
class IdempotencyKeySpec extends Specification {

    // --- Constructor validation ---

    def "null value throws NPE"() {
        when:
        new IdempotencyKey(null)

        then:
        thrown(NullPointerException)
    }

    def "blank value throws IAE"() {
        when:
        new IdempotencyKey("   ")

        then:
        thrown(IllegalArgumentException)
    }

    def "empty value throws IAE"() {
        when:
        new IdempotencyKey("")

        then:
        thrown(IllegalArgumentException)
    }

    def "value over 512 characters throws IAE"() {
        when:
        new IdempotencyKey("x" * 513)

        then:
        thrown(IllegalArgumentException)
    }

    def "value at exactly 512 characters is valid"() {
        when:
        def key = new IdempotencyKey("x" * 512)

        then:
        key.value().length() == 512
    }

    def "valid value creates key"() {
        when:
        def key = new IdempotencyKey("my-key-123")

        then:
        key.value() == "my-key-123"
    }

    // --- temporary / isTemporary ---

    def "temporary creates a temp key"() {
        when:
        def key = IdempotencyKey.temporary()

        then:
        key.isTemporary()
    }

    def "regular key is not temporary"() {
        expect:
        !new IdempotencyKey("regular-key").isTemporary()
    }

    // --- of factory ---

    def "of creates key with given value"() {
        when:
        def key = IdempotencyKey.of("test-key")

        then:
        key.value() == "test-key"
    }

    // --- from with provided key ---

    def "from with provided key uses it directly"() {
        given:
        def payload = new StepsPayload(Instant.now(), Instant.now().plusSeconds(3600), 1000, "com.test")

        when:
        def key = IdempotencyKey.from("my-provided-key", "device1", "StepsBucketedRecorded.v1", payload, 0)

        then:
        key.value() == "my-provided-key"
    }

    // --- from with null key + WorkoutPayload ---

    def "from with null key and WorkoutPayload generates workout-specific key"() {
        given:
        def exercises = [new WorkoutPayload.Exercise("Bench Press", null, null, 1,
                [new WorkoutPayload.ExerciseSet(1, 100.0d, 5, false)])]
        def payload = new WorkoutPayload("workout-abc", Instant.now(), null, null, exercises)

        when:
        def key = IdempotencyKey.from(null, "device1", "WorkoutRecorded.v1", payload, 0)

        then:
        key.value() == "device1|workout|workout-abc"
    }

    // --- from with null key + SleepSessionPayload ---

    def "from with null key and SleepSessionPayload generates sleep-specific key"() {
        given:
        def payload = new SleepSessionPayload("sleep-xyz",
                Instant.parse("2025-06-14T22:00:00Z"),
                Instant.parse("2025-06-15T06:00:00Z"),
                480, "com.test")

        when:
        def key = IdempotencyKey.from(null, "device1", "SleepSessionRecorded.v1", payload, 0)

        then:
        key.value() == "device1|sleep|sleep-xyz"
    }

    // --- from with null key + generic payload ---

    def "from with null key and generic payload generates timestamped key"() {
        given:
        def payload = new StepsPayload(Instant.now(), Instant.now().plusSeconds(3600), 1000, "com.test")

        when:
        def key = IdempotencyKey.from(null, "device1", "StepsBucketedRecorded.v1", payload, 3)

        then:
        key.value().startsWith("device1|StepsBucketedRecorded.v1|")
        key.value().endsWith("-3")
    }

    // --- from with blank key falls through to generation ---

    def "from with blank key generates a key"() {
        given:
        def payload = new StepsPayload(Instant.now(), Instant.now().plusSeconds(3600), 1000, "com.test")

        when:
        def key = IdempotencyKey.from("   ", "device1", "StepsBucketedRecorded.v1", payload, 0)

        then:
        key.value().startsWith("device1|StepsBucketedRecorded.v1|")
    }
}
