package com.healthassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {

    @NotBlank(message = "idempotencyKey is required")
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @NotBlank(message = "type is required")
    @JsonProperty("type")
    private String type;

    @NotNull(message = "occurredAt is required")
    @JsonProperty("occurredAt")
    private Instant occurredAt;

    @NotNull(message = "payload is required")
    @JsonProperty("payload")
    private Map<String, Object> payload;
}

