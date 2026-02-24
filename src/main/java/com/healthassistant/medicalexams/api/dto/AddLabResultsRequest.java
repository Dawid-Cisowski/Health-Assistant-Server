package com.healthassistant.medicalexams.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddLabResultsRequest(
        @NotNull @Size(min = 1) List<@Valid LabResultEntry> results
) {
}
