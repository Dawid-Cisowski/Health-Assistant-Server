package com.healthassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope for a single health event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 512, message = "Idempotency key must be between 8 and 512 characters")
    private String idempotencyKey;

    @NotBlank(message = "Event type is required")
    private String type;

    @NotNull(message = "Occurred at timestamp is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant occurredAt;

    @NotNull(message = "Payload is required")
    private Map<String, Object> payload;
}

