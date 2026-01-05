package com.healthassistant.healthevents.api.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

class EventTypeDeserializer extends JsonDeserializer<EventType> {

    @Override
    public EventType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = extractValue(p);
        return EventType.from(value);
    }

    private String extractValue(JsonParser p) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            if (!node.has("value")) {
                throw new IOException("Missing 'value' field in EventType object");
            }
            return node.get("value").asText();
        }
        return p.getValueAsString();
    }
}
