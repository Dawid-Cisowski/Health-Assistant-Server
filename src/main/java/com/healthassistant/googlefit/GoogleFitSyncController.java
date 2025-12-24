package com.healthassistant.googlefit;

import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.googlefit.api.dto.SyncDatesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/sync/dates")
    @Operation(
            summary = "Schedule Google Fit sync for specific dates",
            description = """
                    Schedules synchronization of Google Fit data for a specific list of dates.
                    Dates are queued for processing and will be synced asynchronously (up to 10 in parallel).
                    Requires HMAC authentication.

                    The synchronization will:
                    1. Create sync tasks for each date in the list
                    2. Skip dates that already have a pending or in-progress task
                    3. Trigger immediate processing of queued tasks
                    4. Tasks are processed in background with retry logic (max 3 attempts)

                    Validation rules:
                    - Maximum 100 dates per request
                    - Each date must be in ISO-8601 format (YYYY-MM-DD)
                    - Dates cannot be in the future
                    - Dates cannot be more than 365 days in the past
                    - No duplicate dates allowed

                    Note: This endpoint returns immediately after scheduling. Check logs for progress.
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Synchronization tasks scheduled"),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation failed)"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Failed to schedule synchronization")
    })
    public ResponseEntity<Map<String, Object>> syncSpecificDates(
            @Valid @RequestBody SyncDatesRequest request
    ) {
        log.info("Google Fit synchronization triggered via API for {} specific dates", request.dates().size());

        try {
            var result = googleFitFacade.syncDates(request.dates());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "scheduled");
            response.put("message", "Historical sync tasks scheduled for processing");
            response.put("scheduledDays", result.processedDays());

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid dates for Google Fit synchronization: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to schedule Google Fit synchronization via API", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to schedule synchronization: " + e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }
}

