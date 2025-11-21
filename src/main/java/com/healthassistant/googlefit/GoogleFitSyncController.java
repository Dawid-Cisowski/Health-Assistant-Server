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
            summary = "Trigger historical Google Fit synchronization",
            description = """
                    Triggers historical synchronization of Google Fit data for the specified number of past days.
                    The synchronization processes data day-by-day, ensuring idempotent event storage.
                    Requires HMAC authentication.

                    The synchronization will:
                    1. Split the time range into daily windows
                    2. For each day, fetch aggregated data with 5-minute buckets
                    3. Fetch sleep sessions for each day
                    4. Map buckets and sessions to domain events
                    5. Store events in the database (with idempotency check)

                    Note: This endpoint does NOT update the lastSyncedAt timestamp, as it's for historical data.
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historical synchronization completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid days parameter"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Synchronization failed - check logs for details")
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
            response.put("status", "success");
            response.put("message", "Historical synchronization completed");
            response.put("processedDays", result.processedDays());
            response.put("failedDays", result.failedDays());
            response.put("totalEvents", result.totalEvents());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to synchronize historical Google Fit data via API", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Synchronization failed: " + e.getMessage());
            
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }
}

