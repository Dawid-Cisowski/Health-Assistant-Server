package com.healthassistant.googlefit.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SyncDatesRequest(
        @NotEmpty(message = "At least one date is required")
        @Size(max = 100, message = "Maximum 100 dates allowed per request")
        List<LocalDate> dates
) {
}
