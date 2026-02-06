package com.healthassistant.security

import spock.lang.Specification
import spock.lang.Title

import java.nio.charset.StandardCharsets

@Title("HmacSignature - HMAC-SHA256 calculation and constant-time verification")
class HmacSignatureSpec extends Specification {

    private static final byte[] TEST_SECRET = "test-secret-key-12345".getBytes(StandardCharsets.UTF_8)

    // --- calculate ---

    def "calculate produces deterministic output (same input = same output)"() {
        given:
        def canonical = "GET\n/v1/steps\n2025-01-15T10:00:00Z\nnonce123\ndevice1\n"

        when:
        def sig1 = HmacSignature.calculate(canonical, TEST_SECRET)
        def sig2 = HmacSignature.calculate(canonical, TEST_SECRET)

        then:
        sig1 == sig2
    }

    def "calculate returns base64-encoded string"() {
        when:
        def sig = HmacSignature.calculate("test", TEST_SECRET)

        then:
        sig != null
        !sig.isEmpty()
        // Base64 should decode without error
        Base64.decoder.decode(sig) != null
    }

    def "calculate produces different output for different inputs"() {
        when:
        def sig1 = HmacSignature.calculate("input1", TEST_SECRET)
        def sig2 = HmacSignature.calculate("input2", TEST_SECRET)

        then:
        sig1 != sig2
    }

    def "calculate produces different output for different secrets"() {
        given:
        def secret1 = "secret-1".getBytes(StandardCharsets.UTF_8)
        def secret2 = "secret-2".getBytes(StandardCharsets.UTF_8)

        when:
        def sig1 = HmacSignature.calculate("same-input", secret1)
        def sig2 = HmacSignature.calculate("same-input", secret2)

        then:
        sig1 != sig2
    }

    // --- verify ---

    def "verify returns true for matching strings"() {
        expect:
        HmacSignature.verify("abc123", "abc123")
    }

    def "verify returns false for different strings"() {
        expect:
        !HmacSignature.verify("abc123", "abc124")
    }

    def "verify returns false for null expected"() {
        expect:
        !HmacSignature.verify(null, "abc123")
    }

    def "verify returns false for null actual"() {
        expect:
        !HmacSignature.verify("abc123", null)
    }

    def "verify returns false for both null"() {
        expect:
        !HmacSignature.verify(null, null)
    }

    // --- round-trip: calculate then verify ---

    def "calculated signature verifies against itself"() {
        given:
        def canonical = "POST\n/v1/health-events\n2025-01-15T10:00:00Z\nnonce-abc\ndevice-1\n{}"

        when:
        def signature = HmacSignature.calculate(canonical, TEST_SECRET)

        then:
        HmacSignature.verify(signature, signature)
    }
}
