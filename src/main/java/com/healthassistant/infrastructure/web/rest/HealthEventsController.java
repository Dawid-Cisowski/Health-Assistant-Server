package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.ingestion.HealthEventsFacade;
import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.dto.HealthEventsRequest;
import com.healthassistant.dto.HealthEventsResponse;
import com.healthassistant.infrastructure.web.rest.mapper.HealthEventsMapper;
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

@RestController
@RequestMapping("/v1/health-events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Events", description = "Store normalized health events")
class HealthEventsController {

    private final HealthEventsFacade healthEventsFacade;

    @PostMapping
    @Operation(
        summary = "Store a batch of health events (append-only)",
        description = "Accepts up to 100 events in one batch. Each event must have a unique idempotencyKey. " +
                     "On duplicate, the server returns status: duplicate for that item.",
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Batch processed",
            content = @Content(schema = @Schema(implementation = HealthEventsResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Malformed JSON or schema violation"),
        @ApiResponse(responseCode = "401", description = "HMAC auth failed"),
        @ApiResponse(responseCode = "413", description = "Too many events in batch")
    })
    ResponseEntity<HealthEventsResponse> storeHealthEvents(
            @Valid @RequestBody HealthEventsRequest request,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Received batch of {} events from device: {}", request.getEvents().size(), deviceId);
        
        StoreHealthEventsCommand command = HealthEventsMapper.toCommand(request, deviceId);
        var result = healthEventsFacade.storeHealthEvents(command);
        HealthEventsResponse response = HealthEventsMapper.toResponse(result);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(response);
    }
}
