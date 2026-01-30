package com.healthassistant.healthevents.api.model;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;

class EventTypeDeserializer extends ValueDeserializer<EventType> {

    @Override
    public EventType deserialize(JsonParser p, DeserializationContext ctxt) {
        String value = extractValue(p, ctxt);
        return EventType.from(value);
    }

    private String extractValue(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = ctxt.readTree(p);
            if (!node.has("value")) {
                throw new IllegalArgumentException("Missing 'value' field in EventType object");
            }
            return node.get("value").asText();
        }
        return p.getString();
    }
}
