package com.healthassistant.healthevents

import spock.lang.Specification
import spock.lang.Title

@Title("HeartRateStats - heart rate min/avg/max validation")
class HeartRateStatsSpec extends Specification {

    // --- Constructor validation ---

    def "valid stats with all equal values"() {
        when:
        def stats = new HeartRateStats(70, 70.0d, 70)

        then:
        stats.min() == 70
        stats.avg() == 70.0d
        stats.max() == 70
    }

    def "valid stats with different values"() {
        when:
        def stats = new HeartRateStats(60, 75.5d, 90)

        then:
        stats.min() == 60
        stats.avg() == 75.5d
        stats.max() == 90
    }

    def "throws NPE for null min"() {
        when:
        new HeartRateStats(null, 70.0d, 80)

        then:
        thrown(NullPointerException)
    }

    def "throws NPE for null avg"() {
        when:
        new HeartRateStats(60, null, 80)

        then:
        thrown(NullPointerException)
    }

    def "throws NPE for null max"() {
        when:
        new HeartRateStats(60, 70.0d, null)

        then:
        thrown(NullPointerException)
    }

    def "throws IAE when min > max"() {
        when:
        new HeartRateStats(100, 90.0d, 80)

        then:
        thrown(IllegalArgumentException)
    }

    def "throws IAE when avg < min"() {
        when:
        new HeartRateStats(80, 70.0d, 90)

        then:
        thrown(IllegalArgumentException)
    }

    def "throws IAE when avg > max"() {
        when:
        new HeartRateStats(60, 95.0d, 90)

        then:
        thrown(IllegalArgumentException)
    }

    // --- Static validate method ---

    def "validate returns empty list for valid values"() {
        expect:
        HeartRateStats.validate(60, 75.0d, 90).isEmpty()
    }

    def "validate returns error when min > max"() {
        when:
        def errors = HeartRateStats.validate(100, 90.0d, 80)

        then:
        errors.size() >= 1
        errors.any { it.field() == "min" }
    }

    def "validate returns error when avg < min"() {
        when:
        def errors = HeartRateStats.validate(80, 70.0d, 90)

        then:
        errors.any { it.field() == "avg" && it.message().contains("less than min") }
    }

    def "validate returns error when avg > max"() {
        when:
        def errors = HeartRateStats.validate(60, 95.0d, 90)

        then:
        errors.any { it.field() == "avg" && it.message().contains("greater than max") }
    }

    def "validate handles null min gracefully"() {
        expect:
        HeartRateStats.validate(null, 70.0d, 80).isEmpty()
    }

    def "validate handles null avg gracefully"() {
        expect:
        HeartRateStats.validate(60, null, 80).isEmpty()
    }

    def "validate handles null max gracefully"() {
        expect:
        HeartRateStats.validate(60, 70.0d, null).isEmpty()
    }

    def "validate detects multiple errors simultaneously"() {
        when: "min > max AND avg > max"
        def errors = HeartRateStats.validate(100, 110.0d, 80)

        then:
        errors.size() >= 2
    }
}
