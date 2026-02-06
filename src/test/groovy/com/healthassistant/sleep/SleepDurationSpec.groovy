package com.healthassistant.sleep

import spock.lang.Specification
import spock.lang.Title

@Title("SleepDuration - sleep duration value object")
class SleepDurationSpec extends Specification {

    // --- Construction ---

    def "negative minutes throws IAE"() {
        when:
        new SleepDuration(-1)

        then:
        thrown(IllegalArgumentException)
    }

    def "zero minutes is valid"() {
        when:
        def duration = new SleepDuration(0)

        then:
        duration.minutes() == 0
    }

    def "ZERO constant has zero minutes"() {
        expect:
        SleepDuration.ZERO.minutes() == 0
    }

    def "of factory method creates duration"() {
        expect:
        SleepDuration.of(420).minutes() == 420
    }

    // --- add ---

    def "add sums two durations"() {
        given:
        def a = SleepDuration.of(200)
        def b = SleepDuration.of(220)

        expect:
        a.add(b).minutes() == 420
    }

    def "add with ZERO returns same value"() {
        given:
        def duration = SleepDuration.of(300)

        expect:
        duration.add(SleepDuration.ZERO).minutes() == 300
    }

    def "add with null throws NPE"() {
        when:
        SleepDuration.of(100).add(null)

        then:
        thrown(NullPointerException)
    }

    // --- isLongerThan / isShorterThan ---

    def "isLongerThan returns true when this is longer"() {
        expect:
        SleepDuration.of(500).isLongerThan(SleepDuration.of(400))
    }

    def "isLongerThan returns false when equal"() {
        expect:
        !SleepDuration.of(400).isLongerThan(SleepDuration.of(400))
    }

    def "isShorterThan returns true when this is shorter"() {
        expect:
        SleepDuration.of(300).isShorterThan(SleepDuration.of(400))
    }

    def "isShorterThan returns false when equal"() {
        expect:
        !SleepDuration.of(400).isShorterThan(SleepDuration.of(400))
    }

    // --- max / min ---

    def "max returns the longer duration"() {
        given:
        def short_ = SleepDuration.of(300)
        def long_ = SleepDuration.of(500)

        expect:
        short_.max(long_).minutes() == 500
        long_.max(short_).minutes() == 500
    }

    def "max returns either when equal"() {
        given:
        def a = SleepDuration.of(400)
        def b = SleepDuration.of(400)

        expect:
        a.max(b).minutes() == 400
    }

    def "min returns the shorter duration"() {
        given:
        def short_ = SleepDuration.of(300)
        def long_ = SleepDuration.of(500)

        expect:
        short_.min(long_).minutes() == 300
        long_.min(short_).minutes() == 300
    }

    // --- isPositive ---

    def "isPositive returns false for zero"() {
        expect:
        !SleepDuration.ZERO.isPositive()
    }

    def "isPositive returns true for positive minutes"() {
        expect:
        SleepDuration.of(1).isPositive()
    }
}
