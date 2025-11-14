package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.sync.GoogleFitSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/google-fit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Google Fit", description = "Google Fit synchronization endpoints")
public class GoogleFitSyncController {

    private final GoogleFitSyncService googleFitSyncService;

    @PostMapping("/sync")
    @Operation(
            summary = "Manually trigger Google Fit synchronization",
            description = """
                    Manually triggers the Google Fit data synchronization. This endpoint is useful for testing 
                    and manual synchronization without waiting for the scheduled task (runs every 15 minutes).
                    
                    The synchronization will:
                    1. Fetch aggregated data from Google Fit API
                    2. Map buckets to domain events
                    3. Store events in the database (with idempotency check)
                    4. Recalculate daily summary for today
                    5. Update last sync timestamp
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Synchronization triggered successfully"),
            @ApiResponse(responseCode = "500", description = "Synchronization failed - check logs for details")
    })
    public ResponseEntity<Map<String, String>> triggerSync() {
        log.info("Manual Google Fit synchronization triggered via API");
        
        try {
            googleFitSyncService.syncAll();
            
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
}

