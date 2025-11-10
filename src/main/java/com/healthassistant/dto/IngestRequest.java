package com.healthassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for batch event ingestion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequest {

    @NotEmpty(message = "Events list cannot be empty")
    @Size(min = 1, max = 100, message = "Events list must contain between 1 and 100 items")
    @Valid
    private List<EventEnvelope> events;
}

