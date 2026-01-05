package com.healthassistant.modulith

import com.healthassistant.BaseIntegrationSpec
import com.healthassistant.healthevents.api.dto.StoredEventData
import com.healthassistant.healthevents.api.dto.events.StepsEventsStoredEvent
import com.healthassistant.healthevents.api.dto.events.WorkoutEventsStoredEvent
import com.healthassistant.healthevents.api.dto.events.SleepEventsStoredEvent
import com.healthassistant.healthevents.api.dto.events.MealsEventsStoredEvent
import com.healthassistant.healthevents.api.dto.payload.EventPayload
import com.healthassistant.healthevents.api.dto.payload.StepsPayload
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload
import com.healthassistant.healthevents.api.dto.payload.MealRecordedPayload
import com.healthassistant.healthevents.api.dto.payload.ActiveCaloriesPayload
import com.healthassistant.healthevents.api.dto.payload.ActiveMinutesPayload
import com.healthassistant.healthevents.api.dto.payload.HeartRatePayload
import com.healthassistant.healthevents.api.dto.payload.DistanceBucketPayload
import com.healthassistant.healthevents.api.dto.payload.MealType
import com.healthassistant.healthevents.api.dto.payload.HealthRating
import com.healthassistant.healthevents.api.model.DeviceId
import com.healthassistant.healthevents.api.model.EventId
import com.healthassistant.healthevents.api.model.EventType
import com.healthassistant.healthevents.api.model.IdempotencyKey
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.modulith.events.core.EventSerializer
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDate

class ModulithEventSerializationSpec extends BaseIntegrationSpec {

    @Autowired
    EventSerializer eventSerializer

    def "should serialize and deserialize StepsEventsStoredEvent with StepsPayload"() {
        given:
        def payload = new StepsPayload(
                Instant.parse("2025-01-01T08:00:00Z"),
                Instant.parse("2025-01-01T09:00:00Z"),
                5000,
                "com.google.android.apps.fitness"
        )
        def storedEvent = createStoredEventData(payload, "StepsBucketedRecorded.v1")
        def event = new StepsEventsStoredEvent(
                List.of(storedEvent),
                Set.of(LocalDate.of(2025, 1, 1))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, StepsEventsStoredEvent)

        then:
        deserialized.events().size() == 1
        deserialized.events()[0].payload() instanceof StepsPayload
        def deserializedPayload = deserialized.events()[0].payload() as StepsPayload
        deserializedPayload.count() == 5000
        deserialized.affectedDates() == Set.of(LocalDate.of(2025, 1, 1))
    }

    def "should serialize and deserialize WorkoutEventsStoredEvent with nested records"() {
        given:
        def exerciseSet = new WorkoutPayload.ExerciseSet(1, 80.0, 10, false)
        def exercise = new WorkoutPayload.Exercise(
                "Bench Press",
                "Chest",
                1,
                List.of(exerciseSet)
        )
        def payload = new WorkoutPayload(
                "workout-001",
                Instant.parse("2025-01-01T18:00:00Z"),
                "GYMRUN_APP",
                "Chest day",
                List.of(exercise)
        )
        def storedEvent = createStoredEventData(payload, "WorkoutRecorded.v1")
        def event = new WorkoutEventsStoredEvent(
                List.of(storedEvent),
                Set.of(LocalDate.of(2025, 1, 1))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, WorkoutEventsStoredEvent)

        then:
        deserialized.events().size() == 1
        def deserializedPayload = deserialized.events()[0].payload() as WorkoutPayload
        deserializedPayload.workoutId() == "workout-001"
        deserializedPayload.exercises().size() == 1
        deserializedPayload.exercises()[0].name() == "Bench Press"
        deserializedPayload.exercises()[0].sets().size() == 1
        deserializedPayload.exercises()[0].sets()[0].weightKg() == 80.0
    }

    def "should serialize and deserialize SleepEventsStoredEvent"() {
        given:
        def payload = new SleepSessionPayload(
                "sleep-001",
                Instant.parse("2025-01-01T23:00:00Z"),
                Instant.parse("2025-01-02T07:00:00Z"),
                480,
                "com.google.android.apps.fitness",
                null,
                null,
                null,
                null,
                85,
                null
        )
        def storedEvent = createStoredEventData(payload, "SleepSessionRecorded.v1")
        def event = new SleepEventsStoredEvent(
                List.of(storedEvent),
                Set.of(LocalDate.of(2025, 1, 2))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, SleepEventsStoredEvent)

        then:
        deserialized.events().size() == 1
        def deserializedPayload = deserialized.events()[0].payload() as SleepSessionPayload
        deserializedPayload.sleepId() == "sleep-001"
        deserializedPayload.totalMinutes() == 480
        deserializedPayload.sleepScore() == 85
    }

    def "should serialize and deserialize MealsEventsStoredEvent with enums"() {
        given:
        def payload = new MealRecordedPayload(
                "Chicken Salad",
                MealType.LUNCH,
                450,
                35,
                15,
                25,
                HealthRating.HEALTHY
        )
        def storedEvent = createStoredEventData(payload, "MealRecorded.v1")
        def event = new MealsEventsStoredEvent(
                List.of(storedEvent),
                Set.of(LocalDate.of(2025, 1, 1))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, MealsEventsStoredEvent)

        then:
        deserialized.events().size() == 1
        def deserializedPayload = deserialized.events()[0].payload() as MealRecordedPayload
        deserializedPayload.title() == "Chicken Salad"
        deserializedPayload.mealType() == MealType.LUNCH
        deserializedPayload.healthRating() == HealthRating.HEALTHY
    }

    @Unroll
    def "should serialize and deserialize #payloadType.simpleName payload"() {
        given:
        def storedEvent = createStoredEventData(payload, eventType)
        def event = new StepsEventsStoredEvent(
                List.of(storedEvent),
                Set.of(LocalDate.of(2025, 1, 1))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, StepsEventsStoredEvent)

        then:
        deserialized.events().size() == 1
        deserialized.events()[0].payload().class == payloadType

        where:
        payloadType              | eventType                        | payload
        StepsPayload             | "StepsBucketedRecorded.v1"       | createStepsPayload()
        ActiveCaloriesPayload    | "ActiveCaloriesBurnedRecorded.v1"| createActiveCaloriesPayload()
        ActiveMinutesPayload     | "ActiveMinutesRecorded.v1"       | createActiveMinutesPayload()
        HeartRatePayload         | "HeartRateSummaryRecorded.v1"    | createHeartRatePayload()
        DistanceBucketPayload    | "DistanceBucketRecorded.v1"      | createDistanceBucketPayload()
    }

    def "should reject unauthorized types during deserialization"() {
        given:
        def maliciousJson = '''
        ["java.lang.Runtime", {"@class": "java.lang.Runtime"}]
        '''

        when:
        eventSerializer.deserialize(maliciousJson, Object)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject deserialization of ProcessBuilder"() {
        given:
        def maliciousJson = '''
        {"@class": "java.lang.ProcessBuilder", "command": ["cat", "/etc/passwd"]}
        '''

        when:
        eventSerializer.deserialize(maliciousJson, Object)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject deserialization of arbitrary java.util classes not in allowlist"() {
        given:
        def maliciousJson = '''
        {"@class": "java.util.concurrent.ForkJoinPool"}
        '''

        when:
        eventSerializer.deserialize(maliciousJson, Object)

        then:
        thrown(IllegalArgumentException)
    }

    def "should handle multiple events in single stored event"() {
        given:
        def payload1 = createStepsPayload()
        def payload2 = new StepsPayload(
                Instant.parse("2025-01-01T10:00:00Z"),
                Instant.parse("2025-01-01T11:00:00Z"),
                3000,
                "com.google.android.apps.fitness"
        )
        def event = new StepsEventsStoredEvent(
                List.of(
                        createStoredEventData(payload1, "StepsBucketedRecorded.v1"),
                        createStoredEventDataWithDifferentId(payload2, "StepsBucketedRecorded.v1", "evt_002")
                ),
                Set.of(LocalDate.of(2025, 1, 1))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, StepsEventsStoredEvent)

        then:
        deserialized.events().size() == 2
        deserialized.events().every { it.payload() instanceof StepsPayload }
        (deserialized.events()[0].payload() as StepsPayload).count() == 5000
        (deserialized.events()[1].payload() as StepsPayload).count() == 3000
    }

    def "should preserve all value object fields after round-trip"() {
        given:
        def payload = createStepsPayload()
        def storedEvent = createStoredEventData(payload, "StepsBucketedRecorded.v1")
        def event = new StepsEventsStoredEvent(
                List.of(storedEvent),
                Set.of(LocalDate.of(2025, 1, 1))
        )

        when:
        def serialized = eventSerializer.serialize(event)
        def deserialized = eventSerializer.deserialize(serialized, StepsEventsStoredEvent)
        def deserializedData = deserialized.events()[0]

        then:
        deserializedData.eventId().value() == "evt_001"
        deserializedData.deviceId().value() == "test-device"
        deserializedData.idempotencyKey().value() == "test-key-001"
        deserializedData.eventType().value() == "StepsBucketedRecorded.v1"
        deserializedData.occurredAt() == Instant.parse("2025-01-01T09:00:00Z")
    }

    private StoredEventData createStoredEventData(EventPayload payload, String eventTypeStr) {
        new StoredEventData(
                IdempotencyKey.of("test-key-001"),
                EventType.from(eventTypeStr),
                Instant.parse("2025-01-01T09:00:00Z"),
                payload,
                DeviceId.of("test-device"),
                EventId.of("evt_001")
        )
    }

    private StoredEventData createStoredEventDataWithDifferentId(EventPayload payload, String eventTypeStr, String eventId) {
        new StoredEventData(
                IdempotencyKey.of("test-key-002"),
                EventType.from(eventTypeStr),
                Instant.parse("2025-01-01T11:00:00Z"),
                payload,
                DeviceId.of("test-device"),
                EventId.of(eventId)
        )
    }

    private static StepsPayload createStepsPayload() {
        new StepsPayload(
                Instant.parse("2025-01-01T08:00:00Z"),
                Instant.parse("2025-01-01T09:00:00Z"),
                5000,
                "com.google.android.apps.fitness"
        )
    }

    private static ActiveCaloriesPayload createActiveCaloriesPayload() {
        new ActiveCaloriesPayload(
                Instant.parse("2025-01-01T08:00:00Z"),
                Instant.parse("2025-01-01T09:00:00Z"),
                150.5,
                "com.google.android.apps.fitness"
        )
    }

    private static ActiveMinutesPayload createActiveMinutesPayload() {
        new ActiveMinutesPayload(
                Instant.parse("2025-01-01T08:00:00Z"),
                Instant.parse("2025-01-01T09:00:00Z"),
                45,
                "com.google.android.apps.fitness"
        )
    }

    private static HeartRatePayload createHeartRatePayload() {
        new HeartRatePayload(
                Instant.parse("2025-01-01T08:00:00Z"),
                Instant.parse("2025-01-01T09:00:00Z"),
                75.5,
                60,
                95,
                120,
                "com.google.android.apps.fitness"
        )
    }

    private static DistanceBucketPayload createDistanceBucketPayload() {
        new DistanceBucketPayload(
                Instant.parse("2025-01-01T08:00:00Z"),
                Instant.parse("2025-01-01T09:00:00Z"),
                2500.0,
                "com.google.android.apps.fitness"
        )
    }
}
