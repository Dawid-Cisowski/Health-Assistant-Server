package com.healthassistant.sleep

import spock.lang.Specification
import spock.lang.Title

@Title("SleepStages - nullable sleep stage data with null-safe addition")
class SleepStagesSpec extends Specification {

    // --- EMPTY ---

    def "EMPTY has all null values"() {
        expect:
        SleepStages.EMPTY.lightMinutes() == null
        SleepStages.EMPTY.deepMinutes() == null
        SleepStages.EMPTY.remMinutes() == null
        SleepStages.EMPTY.awakeMinutes() == null
    }

    def "EMPTY hasData returns false"() {
        expect:
        !SleepStages.EMPTY.hasData()
    }

    // --- of and hasData ---

    def "of with all values has data"() {
        given:
        def stages = SleepStages.of(120, 90, 60, 30)

        expect:
        stages.hasData()
        stages.lightMinutes() == 120
        stages.deepMinutes() == 90
        stages.remMinutes() == 60
        stages.awakeMinutes() == 30
    }

    def "of with single non-null value has data"() {
        expect:
        SleepStages.of(100, null, null, null).hasData()
        SleepStages.of(null, 100, null, null).hasData()
        SleepStages.of(null, null, 100, null).hasData()
        SleepStages.of(null, null, null, 100).hasData()
    }

    // --- *OrZero ---

    def "orZero methods return 0 for null values"() {
        expect:
        SleepStages.EMPTY.lightOrZero() == 0
        SleepStages.EMPTY.deepOrZero() == 0
        SleepStages.EMPTY.remOrZero() == 0
        SleepStages.EMPTY.awakeOrZero() == 0
    }

    def "orZero methods return actual values when present"() {
        given:
        def stages = SleepStages.of(120, 90, 60, 30)

        expect:
        stages.lightOrZero() == 120
        stages.deepOrZero() == 90
        stages.remOrZero() == 60
        stages.awakeOrZero() == 30
    }

    // --- add ---

    def "add null + null = null"() {
        when:
        def result = SleepStages.EMPTY.add(SleepStages.EMPTY)

        then:
        result.lightMinutes() == null
        result.deepMinutes() == null
        result.remMinutes() == null
        result.awakeMinutes() == null
    }

    def "add null + value = value"() {
        given:
        def a = SleepStages.EMPTY
        def b = SleepStages.of(100, 80, 60, 20)

        when:
        def result = a.add(b)

        then:
        result.lightMinutes() == 100
        result.deepMinutes() == 80
        result.remMinutes() == 60
        result.awakeMinutes() == 20
    }

    def "add value + null = value"() {
        given:
        def a = SleepStages.of(100, 80, 60, 20)
        def b = SleepStages.EMPTY

        when:
        def result = a.add(b)

        then:
        result.lightMinutes() == 100
        result.deepMinutes() == 80
        result.remMinutes() == 60
        result.awakeMinutes() == 20
    }

    def "add value + value = sum"() {
        given:
        def a = SleepStages.of(100, 80, 60, 20)
        def b = SleepStages.of(50, 40, 30, 10)

        when:
        def result = a.add(b)

        then:
        result.lightMinutes() == 150
        result.deepMinutes() == 120
        result.remMinutes() == 90
        result.awakeMinutes() == 30
    }

    def "add with mixed nulls sums only non-null fields"() {
        given:
        def a = SleepStages.of(100, null, 60, null)
        def b = SleepStages.of(null, 80, null, 20)

        when:
        def result = a.add(b)

        then:
        result.lightMinutes() == 100
        result.deepMinutes() == 80
        result.remMinutes() == 60
        result.awakeMinutes() == 20
    }
}
