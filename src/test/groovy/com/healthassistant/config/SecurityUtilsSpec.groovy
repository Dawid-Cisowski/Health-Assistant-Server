package com.healthassistant.config

import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

@Title("SecurityUtils - device ID masking and log sanitization")
class SecurityUtilsSpec extends Specification {

    // --- maskDeviceId ---

    def "maskDeviceId returns *** for null"() {
        expect:
        SecurityUtils.maskDeviceId(null) == "***"
    }

    def "maskDeviceId returns *** for string shorter than 8 chars"() {
        expect:
        SecurityUtils.maskDeviceId("short") == "***"
        SecurityUtils.maskDeviceId("1234567") == "***"
    }

    def "maskDeviceId returns *** for empty string"() {
        expect:
        SecurityUtils.maskDeviceId("") == "***"
    }

    def "maskDeviceId masks exactly 8-char string as first4...last4"() {
        expect:
        SecurityUtils.maskDeviceId("12345678") == "1234...5678"
    }

    def "maskDeviceId masks long string showing first and last 4"() {
        expect:
        SecurityUtils.maskDeviceId("abcdefghijklmnop") == "abcd...mnop"
    }

    // --- sanitizeForLog ---

    def "sanitizeForLog returns 'null' for null input"() {
        expect:
        SecurityUtils.sanitizeForLog(null) == "null"
    }

    def "sanitizeForLog replaces carriage return with underscore"() {
        expect:
        SecurityUtils.sanitizeForLog("line1\rline2") == "line1_line2"
    }

    def "sanitizeForLog replaces newline with underscore"() {
        expect:
        SecurityUtils.sanitizeForLog("line1\nline2") == "line1_line2"
    }

    def "sanitizeForLog replaces tab with underscore"() {
        expect:
        SecurityUtils.sanitizeForLog("col1\tcol2") == "col1_col2"
    }

    def "sanitizeForLog replaces mixed control characters"() {
        expect:
        SecurityUtils.sanitizeForLog("a\r\n\tb") == "a___b"
    }

    def "sanitizeForLog truncates strings over 100 characters"() {
        given:
        def longString = "a" * 150

        when:
        def result = SecurityUtils.sanitizeForLog(longString)

        then:
        result.length() == 103 // 100 + "..."
        result.endsWith("...")
    }

    def "sanitizeForLog does not truncate string at exactly 100 characters"() {
        given:
        def exactString = "a" * 100

        when:
        def result = SecurityUtils.sanitizeForLog(exactString)

        then:
        result == exactString
        result.length() == 100
    }

    def "sanitizeForLog passes through clean string unchanged"() {
        expect:
        SecurityUtils.sanitizeForLog("clean input") == "clean input"
    }
}
