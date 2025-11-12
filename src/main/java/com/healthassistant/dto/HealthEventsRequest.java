package com.healthassistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthEventsRequest {

    @Valid
    @NotEmpty(message = "Events list cannot be empty")
    @Size(min = 1, max = 100, message = "Events list must contain between 1 and 100 items")
    private List<EventEnvelope> events;
}
