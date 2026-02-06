package com.healthassistant.healthevents.model

import com.healthassistant.healthevents.api.model.DeviceId
import com.healthassistant.healthevents.api.model.EventId
import spock.lang.Specification
import spock.lang.Title

@Title("DeviceId and EventId - identity value objects with validation")
class DeviceIdAndEventIdSpec extends Specification {

    // ===== DeviceId =====

    def "DeviceId: null throws NPE"() {
        when:
        new DeviceId(null)

        then:
        thrown(NullPointerException)
    }

    def "DeviceId: blank throws IAE"() {
        when:
        new DeviceId("   ")

        then:
        thrown(IllegalArgumentException)
    }

    def "DeviceId: empty throws IAE"() {
        when:
        new DeviceId("")

        then:
        thrown(IllegalArgumentException)
    }

    def "DeviceId: over 128 chars throws IAE"() {
        when:
        new DeviceId("x" * 129)

        then:
        thrown(IllegalArgumentException)
    }

    def "DeviceId: exactly 128 chars is valid"() {
        when:
        def id = new DeviceId("x" * 128)

        then:
        id.value().length() == 128
    }

    def "DeviceId: valid creation"() {
        when:
        def id = new DeviceId("my-device-123")

        then:
        id.value() == "my-device-123"
    }

    def "DeviceId: of factory method"() {
        when:
        def id = DeviceId.of("device-abc")

        then:
        id.value() == "device-abc"
    }

    def "DeviceId: equals and hashCode work (record)"() {
        expect:
        DeviceId.of("abc") == DeviceId.of("abc")
        DeviceId.of("abc").hashCode() == DeviceId.of("abc").hashCode()
        DeviceId.of("abc") != DeviceId.of("def")
    }

    // ===== EventId =====

    def "EventId: null throws NPE"() {
        when:
        new EventId(null)

        then:
        thrown(NullPointerException)
    }

    def "EventId: blank throws IAE"() {
        when:
        new EventId("   ")

        then:
        thrown(IllegalArgumentException)
    }

    def "EventId: empty throws IAE"() {
        when:
        new EventId("")

        then:
        thrown(IllegalArgumentException)
    }

    def "EventId: missing evt_ prefix throws IAE"() {
        when:
        new EventId("no-prefix-123")

        then:
        thrown(IllegalArgumentException)
    }

    def "EventId: over 32 chars throws IAE"() {
        when:
        new EventId("evt_" + "x" * 29) // 4 + 29 = 33

        then:
        thrown(IllegalArgumentException)
    }

    def "EventId: exactly 32 chars is valid"() {
        when:
        def id = new EventId("evt_" + "x" * 28) // 4 + 28 = 32

        then:
        id.value().length() == 32
    }

    def "EventId: valid minimal creation"() {
        when:
        def id = new EventId("evt_abc")

        then:
        id.value() == "evt_abc"
    }

    def "EventId: of factory method"() {
        when:
        def id = EventId.of("evt_xyz")

        then:
        id.value() == "evt_xyz"
    }

    def "EventId: equals and hashCode work (record)"() {
        expect:
        EventId.of("evt_abc") == EventId.of("evt_abc")
        EventId.of("evt_abc").hashCode() == EventId.of("evt_abc").hashCode()
        EventId.of("evt_abc") != EventId.of("evt_def")
    }
}
