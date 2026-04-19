package com.healthassistant.config;

import com.healthassistant.appevents.api.dto.HealthEventRequestDeserializer;
import com.healthassistant.assistant.HealthTools;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.StoredEventDataDeserializer;
import com.healthassistant.healthevents.api.dto.events.ActivityEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.BaseHealthEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.BodyMeasurementsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CaloriesEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.HeartRateEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.MealsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.RestingHeartRateEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.SleepEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.StepsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.WeightEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.WorkoutEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.payload.ActiveCaloriesPayload;
import com.healthassistant.healthevents.api.dto.payload.ActiveMinutesPayload;
import com.healthassistant.healthevents.api.dto.payload.BodyMeasurementPayload;
import com.healthassistant.healthevents.api.dto.payload.DistanceBucketPayload;
import com.healthassistant.healthevents.api.dto.payload.EventCorrectedPayload;
import com.healthassistant.healthevents.api.dto.payload.EventDeletedPayload;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.HeartRatePayload;
import com.healthassistant.healthevents.api.dto.payload.MealRecordedPayload;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import com.healthassistant.healthevents.api.dto.payload.RestingHeartRatePayload;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import com.healthassistant.healthevents.api.dto.payload.StepsPayload;
import com.healthassistant.healthevents.api.dto.payload.WalkingSessionPayload;
import com.healthassistant.healthevents.api.dto.payload.WeightMeasurementPayload;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.List;
import java.util.stream.Stream;

@Configuration
@ImportRuntimeHints(HealthAssistantNativeHints.Hints.class)
public class HealthAssistantNativeHints {

    static class Hints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] FULL_REFLECTION = {
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.DECLARED_CLASSES
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            registerToolReflection(hints);
            registerEventPayloadHierarchy(hints);
            registerStoredEventHierarchy(hints);
            registerEventTypeHierarchy(hints, classLoader);
            registerValueObjects(hints);
            registerDeserializers(hints, classLoader);
            registerLog4j2Resources(hints);
            registerJdkVersionDetectionMethods(hints);
            registerCaffeineCache(hints, classLoader);
        }

        private void registerToolReflection(RuntimeHints hints) {
            // Spring AI MethodToolCallbackProvider discovers @Tool methods via reflection
            hints.reflection().registerType(HealthTools.class,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        }

        private void registerEventPayloadHierarchy(RuntimeHints hints) {
            // Required by ModulithEventSerializerConfig.collectSealedSubtypes() + Jackson polymorphism
            Stream.of(
                EventPayload.class,
                StepsPayload.class,
                DistanceBucketPayload.class,
                HeartRatePayload.class,
                RestingHeartRatePayload.class,
                SleepSessionPayload.class,
                ActiveCaloriesPayload.class,
                ActiveMinutesPayload.class,
                WalkingSessionPayload.class,
                WorkoutPayload.class,
                WorkoutPayload.Exercise.class,
                WorkoutPayload.ExerciseSet.class,
                MealRecordedPayload.class,
                WeightMeasurementPayload.class,
                BodyMeasurementPayload.class,
                EventDeletedPayload.class,
                EventCorrectedPayload.class,
                MealType.class,
                HealthRating.class
            ).forEach(type -> hints.reflection().registerType(type, FULL_REFLECTION));
        }

        private void registerStoredEventHierarchy(RuntimeHints hints) {
            Stream.of(
                BaseHealthEventsStoredEvent.class,
                StepsEventsStoredEvent.class,
                WorkoutEventsStoredEvent.class,
                SleepEventsStoredEvent.class,
                ActivityEventsStoredEvent.class,
                CaloriesEventsStoredEvent.class,
                MealsEventsStoredEvent.class,
                WeightEventsStoredEvent.class,
                BodyMeasurementsEventsStoredEvent.class,
                HeartRateEventsStoredEvent.class,
                RestingHeartRateEventsStoredEvent.class
            ).forEach(type -> hints.reflection().registerType(type, FULL_REFLECTION));
        }

        private void registerEventTypeHierarchy(RuntimeHints hints, ClassLoader classLoader) {
            // EventType interface is public, but its record implementations are package-private
            // — use registerTypeIfPresent with FQN to avoid compilation errors
            hints.reflection().registerType(EventType.class, FULL_REFLECTION);

            List.of(
                "com.healthassistant.healthevents.api.model.StepsBucketedRecorded",
                "com.healthassistant.healthevents.api.model.DistanceBucketRecorded",
                "com.healthassistant.healthevents.api.model.HeartRateSummaryRecorded",
                "com.healthassistant.healthevents.api.model.RestingHeartRateRecorded",
                "com.healthassistant.healthevents.api.model.SleepSessionRecorded",
                "com.healthassistant.healthevents.api.model.ActiveCaloriesBurnedRecorded",
                "com.healthassistant.healthevents.api.model.ActiveMinutesRecorded",
                "com.healthassistant.healthevents.api.model.WalkingSessionRecorded",
                "com.healthassistant.healthevents.api.model.WorkoutRecorded",
                "com.healthassistant.healthevents.api.model.MealRecorded",
                "com.healthassistant.healthevents.api.model.WeightMeasurementRecorded",
                "com.healthassistant.healthevents.api.model.BodyMeasurementRecorded",
                "com.healthassistant.healthevents.api.model.EventDeleted",
                "com.healthassistant.healthevents.api.model.EventCorrected"
            ).forEach(typeName ->
                hints.reflection().registerTypeIfPresent(classLoader, typeName, FULL_REFLECTION)
            );
        }

        private void registerValueObjects(RuntimeHints hints) {
            Stream.of(
                EventId.class,
                DeviceId.class,
                IdempotencyKey.class,
                StoredEventData.class
            ).forEach(type -> hints.reflection().registerType(type, FULL_REFLECTION));
        }

        private void registerCaffeineCache(RuntimeHints hints, ClassLoader classLoader) {
            // Caffeine loads cache implementation classes via Class.forName() at runtime.
            // The class name encodes the configuration: S=Strong, W=WriteExpiry, S=Stats etc.
            // For our config (expireAfterWrite + recordStats): SSSW
            // Register all common SS* variants to cover future config changes.
            List.of(
                "com.github.benmanes.caffeine.cache.SSSW",
                "com.github.benmanes.caffeine.cache.SSSWR",
                "com.github.benmanes.caffeine.cache.SSW",
                "com.github.benmanes.caffeine.cache.SSWR",
                "com.github.benmanes.caffeine.cache.SSMS",
                "com.github.benmanes.caffeine.cache.SSMSW",
                "com.github.benmanes.caffeine.cache.SSMSR",
                "com.github.benmanes.caffeine.cache.SSMSWR",
                "com.github.benmanes.caffeine.cache.SSSMS",
                "com.github.benmanes.caffeine.cache.SSSMSAW",
                "com.github.benmanes.caffeine.cache.SS",
                "com.github.benmanes.caffeine.cache.SSR",
                "com.github.benmanes.caffeine.cache.SSA",
                "com.github.benmanes.caffeine.cache.SSAR",
                "com.github.benmanes.caffeine.cache.SSAW",
                "com.github.benmanes.caffeine.cache.SSAWR"
            ).forEach(typeName ->
                hints.reflection().registerTypeIfPresent(classLoader, typeName,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS)
            );
        }

        private void registerJdkVersionDetectionMethods(RuntimeHints hints) {
            // Spring Boot 4.1.x detects JVM version by checking specific JDK methods via reflection.
            // In native image these methods are not reachable by default, causing JavaVersion.getJavaVersion()
            // to fall back to SEVENTEEN (Java 17) and failing the Java 25 minimum check.
            // Register the probe methods so Spring Boot correctly detects Java 21 at runtime.
            Stream.of(java.util.SortedSet.class, java.io.Console.class, java.text.NumberFormat.class)
                .forEach(clazz -> hints.reflection().registerType(clazz, MemberCategory.INVOKE_PUBLIC_METHODS));
        }

        private void registerLog4j2Resources(RuntimeHints hints) {
            // Log4j2 discovers plugins via Log4j2Plugins.dat — must be included as native image resource.
            // Covers log4j-core, log4j-layout-template-json (JsonTemplateLayout), etc.
            hints.resources().registerPattern("Log4j2Plugins.dat");
        }

        private void registerDeserializers(RuntimeHints hints, ClassLoader classLoader) {
            // HealthEventRequestDeserializer and StoredEventDataDeserializer are public
            Stream.of(
                HealthEventRequestDeserializer.class,
                StoredEventDataDeserializer.class
            ).forEach(type -> hints.reflection().registerType(type,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));

            // EventTypeDeserializer is package-private — register by name
            hints.reflection().registerTypeIfPresent(classLoader,
                "com.healthassistant.healthevents.api.model.EventTypeDeserializer",
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
