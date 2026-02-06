package com.healthassistant.workout

import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

import java.math.BigDecimal
import java.time.LocalDate

@Title("ExerciseStatisticsCalculator - 1RM estimation and progression calculation")
class ExerciseStatisticsCalculatorSpec extends Specification {

    // --- calculateEstimated1RM ---

    def "1RM with 1 rep returns the weight itself"() {
        expect:
        ExerciseStatisticsCalculator.calculateEstimated1RM(new BigDecimal("100"), 1) == new BigDecimal("100.00")
    }

    @Unroll
    def "1RM with #reps reps at #weight kg returns #expected (Brzycki formula)"() {
        expect:
        ExerciseStatisticsCalculator.calculateEstimated1RM(new BigDecimal(weight), reps) == new BigDecimal(expected)

        where:
        weight | reps | expected
        "100"  | 5    | "112.50"   // 100 * 36 / (37 - 5) = 100 * 36 / 32 = 112.50
        "100"  | 10   | "133.33"   // 100 * 36 / (37 - 10) = 100 * 36 / 27 = 133.33
        "80"   | 5    | "90.00"    // 80 * 36 / 32 = 90.00
        "60"   | 3    | "63.53"    // 60 * 36 / (37 - 3) = 60 * 36 / 34 = 63.53
    }

    def "1RM with reps >= 37 uses 2.5x multiplier"() {
        expect:
        ExerciseStatisticsCalculator.calculateEstimated1RM(new BigDecimal("40"), 37) == new BigDecimal("100.00")
        ExerciseStatisticsCalculator.calculateEstimated1RM(new BigDecimal("40"), 50) == new BigDecimal("100.00")
    }

    @Unroll
    def "1RM returns ZERO for invalid inputs: weight=#weight, reps=#reps"() {
        expect:
        ExerciseStatisticsCalculator.calculateEstimated1RM(
                weight == null ? null : new BigDecimal(weight), reps) == BigDecimal.ZERO

        where:
        weight | reps
        null   | 5
        "0"    | 5
        "-10"  | 5
        "100"  | 0
        "100"  | -1
    }

    // --- calculateProgressionPercentage ---

    def "progression with null data points returns zero"() {
        expect:
        ExerciseStatisticsCalculator.calculateProgressionPercentage(null) == BigDecimal.ZERO
    }

    def "progression with empty list returns zero"() {
        expect:
        ExerciseStatisticsCalculator.calculateProgressionPercentage([]) == BigDecimal.ZERO
    }

    def "progression with single data point returns zero"() {
        given:
        def points = [new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 1, 1), new BigDecimal("100"))]

        expect:
        ExerciseStatisticsCalculator.calculateProgressionPercentage(points) == BigDecimal.ZERO
    }

    def "progression with ascending data returns positive percentage"() {
        given:
        def points = [
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 1, 1), new BigDecimal("100")),
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 2, 1), new BigDecimal("120"))
        ]

        when:
        def result = ExerciseStatisticsCalculator.calculateProgressionPercentage(points)

        then:
        result > BigDecimal.ZERO
    }

    def "progression with descending data returns negative percentage"() {
        given:
        def points = [
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 1, 1), new BigDecimal("120")),
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 2, 1), new BigDecimal("100"))
        ]

        when:
        def result = ExerciseStatisticsCalculator.calculateProgressionPercentage(points)

        then:
        result < BigDecimal.ZERO
    }

    def "progression with flat data returns zero"() {
        given:
        def points = [
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 1, 1), new BigDecimal("100")),
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 2, 1), new BigDecimal("100")),
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 3, 1), new BigDecimal("100"))
        ]

        expect:
        ExerciseStatisticsCalculator.calculateProgressionPercentage(points) == new BigDecimal("0.00")
    }

    def "progression with same-day data points returns zero (no time span)"() {
        given:
        def date = LocalDate.of(2025, 1, 1)
        def points = [
                new ExerciseStatisticsCalculator.DataPoint(date, new BigDecimal("100")),
                new ExerciseStatisticsCalculator.DataPoint(date, new BigDecimal("120"))
        ]

        expect:
        ExerciseStatisticsCalculator.calculateProgressionPercentage(points) == new BigDecimal("0.00")
    }

    def "linear regression with perfectly linear data produces exact percentage"() {
        given: "data going from 100 to 200 over 100 days (100% increase)"
        def points = [
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 1, 1), new BigDecimal("100")),
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 2, 20), new BigDecimal("150")),
                new ExerciseStatisticsCalculator.DataPoint(LocalDate.of(2025, 4, 11), new BigDecimal("200"))
        ]

        when:
        def result = ExerciseStatisticsCalculator.calculateProgressionPercentage(points)

        then:
        result == new BigDecimal("100.00")
    }
}
