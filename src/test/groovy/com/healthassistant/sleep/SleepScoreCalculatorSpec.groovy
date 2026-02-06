package com.healthassistant.sleep

import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Title("SleepScoreCalculator - duration and bedtime scoring")
class SleepScoreCalculatorSpec extends Specification {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    def calculator = new SleepScoreCalculator()

    // --- Duration scoring ---

    @Unroll
    def "duration score for #hours hours (#minutes min) is #expectedScore"() {
        given:
        def start = instantAtPolandTime(22, 0)
        def end = start.plusSeconds(minutes * 60L)

        when:
        def score = calculator.calculateScore(start, end, minutes)

        then: "we only check the duration component (bedtime is fixed at 22:00 = 50)"
        score == expectedScore + 50

        where:
        hours | minutes | expectedScore
        7     | 420     | 50    // 7h -> optimal
        8     | 480     | 50    // 8h -> optimal
        6     | 360     | 40    // 6h -> slightly short
        9     | 540     | 40    // 9h -> slightly long
        5     | 300     | 25    // 5h -> short
        10    | 600     | 25    // 10h -> long
        4     | 240     | 10    // <5h -> very short
        3     | 180     | 10    // <5h -> very short
        11    | 660     | 10    // >10h -> very long
        12    | 720     | 10    // >10h -> very long
    }

    // --- Bedtime scoring ---

    @Unroll
    def "bedtime score for sleep starting at #hour:#minute (Poland time) is #expectedScore"() {
        given:
        def start = instantAtPolandTime(hour, minute)
        def end = start.plusSeconds(7 * 3600L) // 7h sleep -> duration score = 50

        when:
        def score = calculator.calculateScore(start, end, 420)

        then: "we only check the bedtime component (duration is fixed at 7h = 50)"
        score == 50 + expectedScore

        where:
        hour | minute | expectedScore
        21   | 0      | 50     // 21:xx -> ideal
        21   | 30     | 50     // 21:xx -> ideal
        22   | 0      | 50     // 22:xx -> ideal
        22   | 59     | 50     // 22:xx -> ideal
        23   | 0      | 40     // 23:xx -> late
        23   | 59     | 40     // 23:xx -> late
        0    | 0      | 30     // 0:xx -> very late
        0    | 30     | 30     // 0:xx -> very late
        1    | 0      | 20     // 1:xx -> extremely late
        1    | 59     | 20     // 1:xx -> extremely late
        2    | 0      | 15     // other -> worst
        15   | 0      | 15     // daytime nap
        20   | 0      | 15     // early evening
    }

    // --- Combined ---

    def "optimal sleep (7h starting at 22:00 Poland) scores 100"() {
        given:
        def start = instantAtPolandTime(22, 0)
        def end = start.plusSeconds(7 * 3600L)

        expect:
        calculator.calculateScore(start, end, 420) == 100
    }

    def "worst case sleep (4h starting at 3:00) scores 25"() {
        given:
        def start = instantAtPolandTime(3, 0)
        def end = start.plusSeconds(4 * 3600L)

        expect:
        calculator.calculateScore(start, end, 240) == 25 // 10 + 15
    }

    private static Instant instantAtPolandTime(int hour, int minute) {
        return LocalDateTime.of(2025, 6, 15, hour, minute)
                .atZone(POLAND_ZONE)
                .toInstant()
    }
}
