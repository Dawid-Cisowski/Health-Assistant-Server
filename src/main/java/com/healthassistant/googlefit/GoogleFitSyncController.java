package com.healthassistant.googlefit;

import com.healthassistant.googlefit.api.GoogleFitFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/google-fit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Google Fit", description = "Google Fit synchronization endpoints")
class GoogleFitSyncController {

    private final GoogleFitFacade googleFitFacade;

    @PostMapping("/sync")
    @Operation(
            summary = "Manually trigger Google Fit synchronization",
            description = """
                    Manually triggers the Google Fit data synchronization. This endpoint is useful for testing
                    and manual synchronization without waiting for the scheduled task (runs every 15 minutes).
                    Requires HMAC authentication.

                    The synchronization will:
                    1. Fetch aggregated data from Google Fit API
                    2. Map buckets to domain events
                    3. Store events in the database (with idempotency check)
                    4. Recalculate daily summary for today
                    5. Update last sync timestamp
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Synchronization triggered successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Synchronization failed - check logs for details")
    })
    public ResponseEntity<Map<String, String>> triggerSync() {
        log.info("Manual Google Fit synchronization triggered via API");
        
        try {
            googleFitFacade.syncAll();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Google Fit synchronization completed successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to synchronize Google Fit data via API", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Synchronization failed: " + e.getMessage());
            
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }

    @PostMapping("/sync/history")
    @Operation(
            summary = "Schedule historical Google Fit synchronization",
            description = """
                    Schedules historical synchronization of Google Fit data for the specified number of past days.
                    Days are queued for processing and will be synced asynchronously (up to 10 in parallel).
                    Requires HMAC authentication.

                    The synchronization will:
                    1. Create sync tasks for each day in the specified range
                    2. Skip days that already have a pending or in-progress task
                    3. Trigger immediate processing of queued tasks
                    4. Tasks are processed in background with retry logic (max 3 attempts)

                    Note: This endpoint returns immediately after scheduling. Check logs for progress.
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Historical synchronization tasks scheduled"),
            @ApiResponse(responseCode = "400", description = "Invalid days parameter"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Failed to schedule synchronization")
    })
    public ResponseEntity<Map<String, Object>> triggerHistoricalSync(
            @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        log.info("Historical Google Fit synchronization triggered via API for {} days", days);

        if (days < 1 || days > 365) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Days parameter must be between 1 and 365");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        try {
            var result = googleFitFacade.syncHistory(days);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "scheduled");
            response.put("message", "Historical sync tasks scheduled for processing");
            response.put("scheduledDays", result.processedDays());

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to schedule historical Google Fit synchronization via API", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to schedule synchronization: " + e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }
}

