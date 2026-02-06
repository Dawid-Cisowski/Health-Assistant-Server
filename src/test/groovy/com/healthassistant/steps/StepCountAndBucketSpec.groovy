package com.healthassistant.steps

import spock.lang.Specification
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Title("StepCount and StepsBucket - step counting value objects")
class StepCountAndBucketSpec extends Specification {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    // ===== StepCount =====

    def "StepCount: negative value throws IAE"() {
        when:
        new StepCount(-1)

        then:
        thrown(IllegalArgumentException)
    }

    def "StepCount: zero is valid"() {
        expect:
        new StepCount(0).value() == 0
    }

    def "StepCount: ZERO constant"() {
        expect:
        StepCount.ZERO.value() == 0
    }

    def "StepCount: of factory"() {
        expect:
        StepCount.of(1500).value() == 1500
    }

    def "StepCount: add sums two counts"() {
        expect:
        StepCount.of(100).add(StepCount.of(200)).value() == 300
    }

    def "StepCount: add with ZERO returns same"() {
        expect:
        StepCount.of(500).add(StepCount.ZERO).value() == 500
    }

    def "StepCount: isPositive returns false for zero"() {
        expect:
        !StepCount.ZERO.isPositive()
    }

    def "StepCount: isPositive returns true for positive"() {
        expect:
        StepCount.of(1).isPositive()
    }

    def "StepCount: isGreaterThan"() {
        expect:
        StepCount.of(200).isGreaterThan(StepCount.of(100))
        !StepCount.of(100).isGreaterThan(StepCount.of(100))
        !StepCount.of(50).isGreaterThan(StepCount.of(100))
    }

    // ===== StepsBucket =====

    def "StepsBucket.create extracts date and hour from bucketStart in Poland timezone"() {
        given: "14:30 Poland time = 12:30 UTC (summer CEST)"
        def bucketStart = LocalDateTime.of(2025, 6, 15, 14, 0)
                .atZone(POLAND_ZONE).toInstant()
        def bucketEnd = LocalDateTime.of(2025, 6, 15, 15, 0)
                .atZone(POLAND_ZONE).toInstant()

        when:
        def bucket = StepsBucket.create("dev1", bucketStart, bucketEnd, 1500)

        then:
        bucket.date() == LocalDate.of(2025, 6, 15)
        bucket.hour() == 14
        bucket.stepCount() == 1500
        bucket.deviceId() == "dev1"
    }

    def "StepsBucket.create with zero steps"() {
        given:
        def bucketStart = Instant.parse("2025-01-15T10:00:00Z")
        def bucketEnd = Instant.parse("2025-01-15T11:00:00Z")

        when:
        def bucket = StepsBucket.create("dev1", bucketStart, bucketEnd, 0)

        then:
        !bucket.hasSteps()
        bucket.stepCount() == 0
    }

    def "StepsBucket.hasSteps returns true for positive count"() {
        given:
        def bucketStart = Instant.parse("2025-01-15T10:00:00Z")
        def bucketEnd = Instant.parse("2025-01-15T11:00:00Z")

        when:
        def bucket = StepsBucket.create("dev1", bucketStart, bucketEnd, 100)

        then:
        bucket.hasSteps()
    }

    def "StepsBucket constructor throws for hour out of range"() {
        when:
        new StepsBucket("dev1", LocalDate.now(), 24, StepCount.of(100),
                Instant.now(), Instant.now().plusSeconds(3600))

        then:
        thrown(IllegalArgumentException)
    }

    def "StepsBucket constructor throws for negative hour"() {
        when:
        new StepsBucket("dev1", LocalDate.now(), -1, StepCount.of(100),
                Instant.now(), Instant.now().plusSeconds(3600))

        then:
        thrown(IllegalArgumentException)
    }

    def "StepsBucket constructor throws for null deviceId"() {
        when:
        new StepsBucket(null, LocalDate.now(), 10, StepCount.of(100),
                Instant.now(), Instant.now().plusSeconds(3600))

        then:
        thrown(NullPointerException)
    }

    def "StepsBucket constructor throws when bucketStart >= bucketEnd"() {
        given:
        def instant = Instant.parse("2025-01-15T10:00:00Z")

        when:
        new StepsBucket("dev1", LocalDate.now(), 10, StepCount.of(100), instant, instant)

        then:
        thrown(IllegalArgumentException)
    }
}
