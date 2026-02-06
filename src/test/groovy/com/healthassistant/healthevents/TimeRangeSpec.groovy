package com.healthassistant.healthevents

import spock.lang.Specification
import spock.lang.Title

import java.time.Instant

@Title("TimeRange - time interval value object with containment and overlap")
class TimeRangeSpec extends Specification {

    private static final Instant T1 = Instant.parse("2025-01-01T10:00:00Z")
    private static final Instant T2 = Instant.parse("2025-01-01T11:00:00Z")
    private static final Instant T3 = Instant.parse("2025-01-01T12:00:00Z")
    private static final Instant T4 = Instant.parse("2025-01-01T13:00:00Z")

    // --- Constructor validation ---

    def "constructor throws NPE for null start"() {
        when:
        new TimeRange(null, T2)

        then:
        thrown(NullPointerException)
    }

    def "constructor throws NPE for null end"() {
        when:
        new TimeRange(T1, null)

        then:
        thrown(NullPointerException)
    }

    def "constructor throws IAE when start equals end"() {
        when:
        new TimeRange(T1, T1)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor throws IAE when start is after end"() {
        when:
        new TimeRange(T2, T1)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor succeeds with valid range"() {
        when:
        def range = new TimeRange(T1, T2)

        then:
        range.start() == T1
        range.end() == T2
    }

    // --- ofNullable ---

    def "ofNullable returns empty for null start"() {
        expect:
        TimeRange.ofNullable(null, T2).isEmpty()
    }

    def "ofNullable returns empty for null end"() {
        expect:
        TimeRange.ofNullable(T1, null).isEmpty()
    }

    def "ofNullable returns empty for invalid range"() {
        expect:
        TimeRange.ofNullable(T2, T1).isEmpty()
    }

    def "ofNullable returns present for valid range"() {
        when:
        def result = TimeRange.ofNullable(T1, T2)

        then:
        result.isPresent()
        result.get().start() == T1
        result.get().end() == T2
    }

    // --- validate ---

    def "validate returns empty for valid range"() {
        expect:
        TimeRange.validate(T1, T2, "end", "start").isEmpty()
    }

    def "validate returns empty when start is null"() {
        expect:
        TimeRange.validate(null, T2, "end", "start").isEmpty()
    }

    def "validate returns empty when end is null"() {
        expect:
        TimeRange.validate(T1, null, "end", "start").isEmpty()
    }

    def "validate returns error when start is not before end"() {
        when:
        def result = TimeRange.validate(T2, T1, "bucketEnd", "bucketStart")

        then:
        result.isPresent()
        result.get().field() == "bucketEnd"
        result.get().message().contains("bucketStart")
    }

    // --- duration ---

    def "duration returns correct duration"() {
        given:
        def range = new TimeRange(T1, T2)

        expect:
        range.duration().toMinutes() == 60L
    }

    def "durationMinutes returns minutes"() {
        given:
        def range = new TimeRange(T1, T3) // 2 hours

        expect:
        range.durationMinutes() == 120L
    }

    // --- contains ---

    def "contains returns true for instant at start (inclusive)"() {
        given:
        def range = new TimeRange(T1, T3)

        expect:
        range.contains(T1)
    }

    def "contains returns false for instant at end (exclusive)"() {
        given:
        def range = new TimeRange(T1, T3)

        expect:
        !range.contains(T3)
    }

    def "contains returns true for instant inside range"() {
        given:
        def range = new TimeRange(T1, T3)

        expect:
        range.contains(T2)
    }

    def "contains returns false for instant before range"() {
        given:
        def range = new TimeRange(T2, T3)

        expect:
        !range.contains(T1)
    }

    def "contains returns false for instant after range"() {
        given:
        def range = new TimeRange(T1, T2)

        expect:
        !range.contains(T3)
    }

    // --- overlaps ---

    def "overlaps returns true for overlapping ranges"() {
        given:
        def range1 = new TimeRange(T1, T3) // 10:00-12:00
        def range2 = new TimeRange(T2, T4) // 11:00-13:00

        expect:
        range1.overlaps(range2)
        range2.overlaps(range1)
    }

    def "overlaps returns false for adjacent ranges (no overlap)"() {
        given:
        def range1 = new TimeRange(T1, T2) // 10:00-11:00
        def range2 = new TimeRange(T2, T3) // 11:00-12:00

        expect:
        !range1.overlaps(range2)
        !range2.overlaps(range1)
    }

    def "overlaps returns true when one range contains the other"() {
        given:
        def outer = new TimeRange(T1, T4) // 10:00-13:00
        def inner = new TimeRange(T2, T3) // 11:00-12:00

        expect:
        outer.overlaps(inner)
        inner.overlaps(outer)
    }

    def "overlaps returns false for disjoint ranges"() {
        given:
        def range1 = new TimeRange(T1, T2) // 10:00-11:00
        def range2 = new TimeRange(T3, T4) // 12:00-13:00

        expect:
        !range1.overlaps(range2)
        !range2.overlaps(range1)
    }
}
