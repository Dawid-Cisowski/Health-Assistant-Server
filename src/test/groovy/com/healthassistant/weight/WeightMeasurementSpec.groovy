package com.healthassistant.weight

import spock.lang.Specification
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate

@Title("WeightMeasurement - weight measurement value object with body composition")
class WeightMeasurementSpec extends Specification {

    // --- Null required fields ---

    def "throws NPE for null deviceId"() {
        when:
        createMeasurement(deviceId: null)

        then:
        thrown(NullPointerException)
    }

    def "throws NPE for null eventId"() {
        when:
        createMeasurement(eventId: null)

        then:
        thrown(NullPointerException)
    }

    def "throws NPE for null weightKg"() {
        when:
        createMeasurement(weightKg: null)

        then:
        thrown(NullPointerException)
    }

    // --- Weight bounds ---

    def "weight below 1kg throws IAE"() {
        when:
        createMeasurement(weightKg: new BigDecimal("0.99"))

        then:
        thrown(IllegalArgumentException)
    }

    def "weight above 700kg throws IAE"() {
        when:
        createMeasurement(weightKg: new BigDecimal("700.01"))

        then:
        thrown(IllegalArgumentException)
    }

    def "weight at exactly 1kg is valid"() {
        when:
        def m = createMeasurement(weightKg: BigDecimal.ONE)

        then:
        m.weightKg() == BigDecimal.ONE
    }

    def "weight at exactly 700kg is valid"() {
        when:
        def m = createMeasurement(weightKg: new BigDecimal("700"))

        then:
        m.weightKg() == new BigDecimal("700")
    }

    // --- Score bounds ---

    def "score null is valid"() {
        when:
        def m = createMeasurement(score: null)

        then:
        m.score() == null
    }

    def "score below 0 throws IAE"() {
        when:
        createMeasurement(score: -1)

        then:
        thrown(IllegalArgumentException)
    }

    def "score above 100 throws IAE"() {
        when:
        createMeasurement(score: 101)

        then:
        thrown(IllegalArgumentException)
    }

    def "score at 0 is valid"() {
        when:
        def m = createMeasurement(score: 0)

        then:
        m.score() == 0
    }

    def "score at 100 is valid"() {
        when:
        def m = createMeasurement(score: 100)

        then:
        m.score() == 100
    }

    // --- create factory: date from measuredAt in Poland TZ ---

    def "create extracts date from measuredAt in Poland timezone"() {
        given: "measuredAt is 2025-06-15 at 06:00 UTC = 08:00 CEST"
        def measuredAt = Instant.parse("2025-06-15T06:00:00Z")

        when:
        def m = WeightMeasurement.create(
                "dev1", "evt_1", "m1", measuredAt, null, new BigDecimal("80"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)

        then:
        m.date() == LocalDate.of(2025, 6, 15)
    }

    def "create handles midnight crossing in Poland timezone"() {
        given: "23:30 UTC on Jan 14 = 00:30 CET on Jan 15 in winter"
        def measuredAt = Instant.parse("2025-01-14T23:30:00Z")

        when:
        def m = WeightMeasurement.create(
                "dev1", "evt_1", "m1", measuredAt, null, new BigDecimal("80"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)

        then:
        m.date() == LocalDate.of(2025, 1, 15) // CET is UTC+1
    }

    // --- hasBodyCompositionData ---

    def "hasBodyCompositionData returns true when both bodyFat and muscle are present"() {
        when:
        def m = createMeasurement(bodyFatPercent: new BigDecimal("15"), musclePercent: new BigDecimal("40"))

        then:
        m.hasBodyCompositionData()
    }

    def "hasBodyCompositionData returns false when bodyFat is null"() {
        when:
        def m = createMeasurement(bodyFatPercent: null, musclePercent: new BigDecimal("40"))

        then:
        !m.hasBodyCompositionData()
    }

    def "hasBodyCompositionData returns false when muscle is null"() {
        when:
        def m = createMeasurement(bodyFatPercent: new BigDecimal("15"), musclePercent: null)

        then:
        !m.hasBodyCompositionData()
    }

    // --- isHealthyScore ---

    def "isHealthyScore returns false for null score"() {
        when:
        def m = createMeasurement(score: null)

        then:
        !m.isHealthyScore()
    }

    def "isHealthyScore returns false for score 69"() {
        when:
        def m = createMeasurement(score: 69)

        then:
        !m.isHealthyScore()
    }

    def "isHealthyScore returns true for score 70"() {
        when:
        def m = createMeasurement(score: 70)

        then:
        m.isHealthyScore()
    }

    def "isHealthyScore returns true for score 100"() {
        when:
        def m = createMeasurement(score: 100)

        then:
        m.isHealthyScore()
    }

    // --- Helper ---

    private static WeightMeasurement createMeasurement(Map overrides = [:]) {
        def defaults = [
                deviceId       : "dev1",
                eventId        : "evt_1",
                measurementId  : "m1",
                date           : LocalDate.of(2025, 6, 15),
                measuredAt     : Instant.parse("2025-06-15T10:00:00Z"),
                score          : (Integer) null,
                weightKg       : new BigDecimal("80"),
                bmi            : null,
                bodyFatPercent : null,
                musclePercent  : null,
                hydrationPercent: null,
                boneMassKg     : null,
                bmrKcal        : null,
                visceralFatLevel: null,
                subcutaneousFatPercent: null,
                proteinPercent : null,
                metabolicAge   : null,
                idealWeightKg  : null,
                weightControlKg: null,
                fatMassKg      : null,
                leanBodyMassKg : null,
                muscleMassKg   : null,
                proteinMassKg  : null,
                bodyType       : null,
                source         : null
        ]
        def merged = defaults + overrides

        return new WeightMeasurement(
                merged.deviceId as String,
                merged.eventId as String,
                merged.measurementId as String,
                merged.date as LocalDate,
                merged.measuredAt as Instant,
                merged.score as Integer,
                merged.weightKg as BigDecimal,
                merged.bmi as BigDecimal,
                merged.bodyFatPercent as BigDecimal,
                merged.musclePercent as BigDecimal,
                merged.hydrationPercent as BigDecimal,
                merged.boneMassKg as BigDecimal,
                merged.bmrKcal as Integer,
                merged.visceralFatLevel as Integer,
                merged.subcutaneousFatPercent as BigDecimal,
                merged.proteinPercent as BigDecimal,
                merged.metabolicAge as Integer,
                merged.idealWeightKg as BigDecimal,
                merged.weightControlKg as BigDecimal,
                merged.fatMassKg as BigDecimal,
                merged.leanBodyMassKg as BigDecimal,
                merged.muscleMassKg as BigDecimal,
                merged.proteinMassKg as BigDecimal,
                merged.bodyType as String,
                merged.source as String
        )
    }
}
