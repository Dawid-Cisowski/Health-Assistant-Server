package com.healthassistant.medicalexamimport.api.dto;

import java.util.UUID;

public record ConfirmDraftRequest(UUID relatedExaminationId) {
}
