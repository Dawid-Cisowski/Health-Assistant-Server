package com.healthassistant

import com.healthassistant.config.SecurityUtils
import spock.lang.Specification
import spock.lang.Title

@Title("Unit: SecurityUtils - log sanitization and device ID masking")
class SecurityUtilsSpec extends Specification {

    // --- maskDeviceId ---

    def "maskDeviceId returns *** for null input"() {
        expect:
        SecurityUtils.maskDeviceId(null) == "***"
    }

    def "maskDeviceId returns *** for empty string"() {
        expect:
        SecurityUtils.maskDeviceId("") == "***"
    }

    def "maskDeviceId returns *** for string shorter than 8 characters"() {
        expect:
        SecurityUtils.maskDeviceId("abcdefg") == "***"
    }

    def "maskDeviceId masks 8-character string showing first 4 and last 4"() {
        expect:
        SecurityUtils.maskDeviceId("12345678") == "1234...5678"
    }

    def "maskDeviceId masks long string showing first 4 and last 4"() {
        expect:
        SecurityUtils.maskDeviceId("abcdefghijklmnop") == "abcd...mnop"
    }

    // --- sanitizeForLog ---

    def "sanitizeForLog returns 'null' for null input"() {
        expect:
        SecurityUtils.sanitizeForLog(null) == "null"
    }

    def "sanitizeForLog returns normal string unchanged"() {
        expect:
        SecurityUtils.sanitizeForLog("hello world") == "hello world"
    }

    def "sanitizeForLog replaces CR, LF and TAB with underscores"() {
        expect:
        SecurityUtils.sanitizeForLog("line1\r\nline2\ttab") == "line1__line2_tab"
    }

    def "sanitizeForLog truncates strings longer than 100 characters"() {
        given:
        def input = "a" * 150

        when:
        def result = SecurityUtils.sanitizeForLog(input)

        then:
        result.length() == 103
        result.endsWith("...")
        result.startsWith("a" * 100)
    }

    def "sanitizeForLog does not truncate string of exactly 100 characters"() {
        given:
        def input = "b" * 100

        expect:
        SecurityUtils.sanitizeForLog(input) == input
    }
}
