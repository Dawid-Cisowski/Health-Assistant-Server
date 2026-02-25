package com.healthassistant.medicalexams.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkExaminationRequest(@NotNull UUID linkedExaminationId) {
}
