package com.healthassistant.controller;

import com.healthassistant.dto.IngestRequest;
import com.healthassistant.dto.IngestResponse;
import com.healthassistant.service.EventIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for event ingestion endpoints
 */
@RestController
@RequestMapping("/v1/ingest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Ingestion", description = "Batch ingest of normalized health events")
public class IngestController {

    private final EventIngestionService ingestionService;

    @PostMapping("/events")
    @Operation(
        summary = "Ingest a batch of events (append-only)",
        description = "Accepts up to 100 events in one batch. Each event must have a unique idempotencyKey. " +
                     "On duplicate, the server returns status: duplicate for that item.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Batch processed",
            content = @Content(schema = @Schema(implementation = IngestResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Malformed JSON or schema violation"),
        @ApiResponse(responseCode = "401", description = "HMAC auth failed"),
        @ApiResponse(responseCode = "413", description = "Too many events in batch")
    })
    public ResponseEntity<IngestResponse> ingestEvents(
            @Valid @RequestBody IngestRequest request,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Received batch of {} events from device: {}", request.getEvents().size(), deviceId);
        
        IngestResponse response = ingestionService.ingestEvents(request, deviceId);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(response);
    }
}

