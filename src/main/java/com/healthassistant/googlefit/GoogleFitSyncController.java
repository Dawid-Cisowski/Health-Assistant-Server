package com.healthassistant.googlefit;

import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.googlefit.api.SyncDayResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/google-fit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Google Fit", description = "Google Fit synchronization endpoints")
class GoogleFitSyncController {

    private final GoogleFitFacade googleFitFacade;

    @PostMapping("/sync/day")
    @Operation(
            summary = "Sync Google Fit data for a specific date",
            description = "Synchronously fetches and stores Google Fit data for the specified date.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Synchronization completed"),
            @ApiResponse(responseCode = "400", description = "Invalid date"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    public ResponseEntity<SyncDayResult> syncDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Google Fit sync triggered for date: {}", date);
        SyncDayResult result = googleFitFacade.syncDay(date);
        return ResponseEntity.ok(result);
    }
}
