package com.healthassistant.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Administrative operations for data management")
class AdminController {

    private final ReprojectionService reprojectionService;

    @PostMapping("/reproject")
    @Operation(
            summary = "Rebuild all projections from health events",
            description = "Deletes all existing projections and rebuilds them from the health_events table. " +
                    "Use this to recover from data inconsistencies or after manual database changes. " +
                    "This operation may take a while depending on the number of events. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reprojection completed successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Error occurred during reprojection")
    })
    ResponseEntity<ReprojectionService.ReprojectionResult> reproject() {
        log.warn("POST /v1/admin/reproject endpoint called - rebuilding all projections");
        ReprojectionService.ReprojectionResult result = reprojectionService.reprojectAll();
        return ResponseEntity.ok(result);
    }
}
