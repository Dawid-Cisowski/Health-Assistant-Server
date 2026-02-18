package com.healthassistant.appevents

import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventError
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventResult
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventStatus
import com.healthassistant.healthevents.api.model.EventId
import spock.lang.Specification
import spock.lang.Title

@Title("HealthEventsResponseMapper - maps internal results to API response")
class HealthEventsResponseMapperSpec extends Specification {

    def mapper = new HealthEventsResponseMapper()

    // --- determineOverallStatus (via toResponse) ---

    def "all events invalid -> status 'all_invalid'"() {
        given:
        def results = [
                new EventResult(0, EventStatus.INVALID, null, new EventError("payload", "bad")),
                new EventResult(1, EventStatus.INVALID, null, new EventError("payload", "bad"))
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 2)

        then:
        response.status() == "all_invalid"
    }

    def "some events invalid -> status 'partial_success'"() {
        given:
        def results = [
                new EventResult(0, EventStatus.STORED, EventId.of("evt_abc"), null),
                new EventResult(1, EventStatus.INVALID, null, new EventError("payload", "bad"))
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 2)

        then:
        response.status() == "partial_success"
    }

    def "no invalid events -> status 'success'"() {
        given:
        def results = [
                new EventResult(0, EventStatus.STORED, EventId.of("evt_abc"), null),
                new EventResult(1, EventStatus.DUPLICATE, EventId.of("evt_def"), null)
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 2)

        then:
        response.status() == "success"
    }

    def "all stored events -> status 'success'"() {
        given:
        def results = [
                new EventResult(0, EventStatus.STORED, EventId.of("evt_abc"), null)
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 1)

        then:
        response.status() == "success"
    }

    // --- Summary counts ---

    def "summary counts stored, duplicate, and invalid correctly"() {
        given:
        def results = [
                new EventResult(0, EventStatus.STORED, EventId.of("evt_a01"), null),
                new EventResult(1, EventStatus.STORED, EventId.of("evt_a02"), null),
                new EventResult(2, EventStatus.DUPLICATE, EventId.of("evt_a03"), null),
                new EventResult(3, EventStatus.INVALID, null, new EventError("field", "msg")),
                new EventResult(4, EventStatus.INVALID, null, new EventError("field", "msg"))
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 5)

        then:
        response.summary().stored() == 2L
        response.summary().duplicate() == 1L
        response.summary().invalid() == 2L
        response.totalEvents() == 5
    }

    def "empty results produce all-zero summary"() {
        given:
        def storeResult = new StoreHealthEventsResult([], Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 0)

        then:
        response.summary().stored() == 0L
        response.summary().duplicate() == 0L
        response.summary().invalid() == 0L
    }

    // --- Event results mapping ---

    def "event results preserve index, status, eventId, and error details"() {
        given:
        def results = [
                new EventResult(0, EventStatus.STORED, EventId.of("evt_abc"), null),
                new EventResult(1, EventStatus.INVALID, null, new EventError("payload", "Missing required field"))
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 2)

        then:
        response.events().size() == 2

        and: "first event: stored with eventId"
        response.events()[0].index() == 0
        response.events()[0].status() == "stored"
        response.events()[0].eventId() == "evt_abc"
        response.events()[0].error() == null

        and: "second event: invalid with error"
        response.events()[1].index() == 1
        response.events()[1].status() == "invalid"
        response.events()[1].eventId() == null
        response.events()[1].error().field() == "payload"
        response.events()[1].error().message() == "Missing required field"
    }

    def "duplicate event result maps correctly"() {
        given:
        def results = [
                new EventResult(0, EventStatus.DUPLICATE, EventId.of("evt_dup"), null)
        ]
        def storeResult = new StoreHealthEventsResult(results, Set.of(), Set.of(), List.of())

        when:
        def response = mapper.toResponse(storeResult, 1)

        then:
        response.events()[0].status() == "duplicate"
        response.events()[0].eventId() == "evt_dup"
    }
}
