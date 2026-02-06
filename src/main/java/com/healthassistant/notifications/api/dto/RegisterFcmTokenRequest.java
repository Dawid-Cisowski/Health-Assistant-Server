package com.healthassistant.notifications.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to register or update an FCM push notification token")
public record RegisterFcmTokenRequest(
    @NotBlank
    @Size(max = 4096)
    @Schema(description = "Firebase Cloud Messaging token", example = "fcm-token-abc123...")
    String token
) {}
