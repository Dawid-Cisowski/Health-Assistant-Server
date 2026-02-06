package com.healthassistant.sleep

import spock.lang.Specification
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Title("SleepSession - sleep session creation and date extraction")
class SleepSessionSpec extends Specification {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    // --- create (3-arg overload: without stages) ---

    def "create extracts date from sleepEnd in Poland timezone"() {
        given: "sleep ending at 7:00 AM Warsaw on 2025-06-15"
        def sleepStart = Instant.parse("2025-06-14T21:00:00Z") // 23:00 CEST
        def sleepEnd = Instant.parse("2025-06-15T05:00:00Z")   // 07:00 CEST

        when:
        def session = SleepSession.create("dev1", "sleep1", "evt_1", sleepStart, sleepEnd, 480, "com.test")

        then:
        session.date() == LocalDate.of(2025, 6, 15)
        session.deviceId() == "dev1"
        session.sleepId() == "sleep1"
        session.eventId() == "evt_1"
        session.duration().minutes() == 480
        session.stages() == SleepStages.EMPTY
        session.sleepScore() == null
        session.originPackage() == "com.test"
    }

    // --- create with stages ---

    def "create with stages preserves stage data"() {
        given:
        def start = Instant.parse("2025-06-14T21:00:00Z")
        def end = Instant.parse("2025-06-15T05:00:00Z")
        def stages = SleepStages.of(120, 90, 60, 30)

        when:
        def session = SleepSession.create("dev1", "sleep1", "evt_1", start, end, 480, stages, "com.test")

        then:
        session.stages() == stages
        session.sleepScore() == null
    }

    // --- create with stages and score ---

    def "create with stages and score preserves all data"() {
        given:
        def start = Instant.parse("2025-06-14T21:00:00Z")
        def end = Instant.parse("2025-06-15T05:00:00Z")
        def stages = SleepStages.of(120, 90, 60, 30)

        when:
        def session = SleepSession.create("dev1", "sleep1", "evt_1", start, end, 480, stages, 85, "com.test")

        then:
        session.stages() == stages
        session.sleepScore() == 85
    }

    // --- Date extraction edge case: midnight crossing ---

    def "date uses sleepEnd for date assignment (midnight crossing)"() {
        given: "sleep starting before midnight UTC, ending after midnight Warsaw"
        def sleepStart = Instant.parse("2025-01-14T22:00:00Z") // 23:00 CET Jan 14
        def sleepEnd = Instant.parse("2025-01-15T06:00:00Z")   // 07:00 CET Jan 15

        when:
        def session = SleepSession.create("dev1", "sleep1", "evt_1", sleepStart, sleepEnd, 480, "com.test")

        then:
        session.date() == LocalDate.of(2025, 1, 15)
    }

    // --- Validation ---

    def "constructor throws NPE for null deviceId"() {
        when:
        SleepSession.create(null, "sleep1", "evt_1",
                Instant.parse("2025-06-14T22:00:00Z"),
                Instant.parse("2025-06-15T06:00:00Z"),
                480, "com.test")

        then:
        thrown(NullPointerException)
    }

    def "constructor throws IAE when sleepStart is not before sleepEnd"() {
        given:
        def instant = Instant.parse("2025-06-15T06:00:00Z")

        when:
        SleepSession.create("dev1", "sleep1", "evt_1", instant, instant, 0, "com.test")

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor throws IAE when sleepStart is after sleepEnd"() {
        given:
        def sleepStart = Instant.parse("2025-06-15T08:00:00Z")
        def sleepEnd = Instant.parse("2025-06-15T06:00:00Z")

        when:
        SleepSession.create("dev1", "sleep1", "evt_1", sleepStart, sleepEnd, 0, "com.test")

        then:
        thrown(IllegalArgumentException)
    }

    // --- hasValidDuration ---

    def "hasValidDuration returns true for positive duration"() {
        given:
        def session = SleepSession.create("dev1", "sleep1", "evt_1",
                Instant.parse("2025-06-14T22:00:00Z"),
                Instant.parse("2025-06-15T06:00:00Z"),
                480, "com.test")

        expect:
        session.hasValidDuration()
    }

    def "hasValidDuration returns false for zero duration"() {
        given:
        def session = SleepSession.create("dev1", "sleep1", "evt_1",
                Instant.parse("2025-06-14T22:00:00Z"),
                Instant.parse("2025-06-15T06:00:00Z"),
                0, "com.test")

        expect:
        !session.hasValidDuration()
    }

    // --- durationMinutes ---

    def "durationMinutes delegates to duration"() {
        given:
        def session = SleepSession.create("dev1", "sleep1", "evt_1",
                Instant.parse("2025-06-14T22:00:00Z"),
                Instant.parse("2025-06-15T06:00:00Z"),
                480, "com.test")

        expect:
        session.durationMinutes() == 480
    }
}
