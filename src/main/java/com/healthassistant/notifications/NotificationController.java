package com.healthassistant.notifications;

import com.healthassistant.notifications.api.NotificationFacade;
import com.healthassistant.notifications.api.dto.RegisterFcmTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Push notification management")
class NotificationController {

    private final NotificationFacade notificationFacade;

    @PostMapping("/fcm-token")
    @Operation(
            summary = "Register or update FCM token",
            description = "Registers or updates the Firebase Cloud Messaging token for the authenticated device. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<Void> registerFcmToken(
            @Valid @RequestBody RegisterFcmTokenRequest request,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("FCM token registration request from device {}", maskDeviceId(deviceId));
        notificationFacade.registerFcmToken(deviceId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/fcm-token")
    @Operation(
            summary = "Deactivate FCM token",
            description = "Deactivates the FCM token for the authenticated device. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token deactivated successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<Void> deactivateFcmToken(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("FCM token deactivation request from device {}", maskDeviceId(deviceId));
        notificationFacade.deactivateToken(deviceId);
        return ResponseEntity.ok().build();
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
