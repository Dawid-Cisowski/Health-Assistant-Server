package com.healthassistant.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.assistant.api.AssistantFacade;
import com.healthassistant.assistant.api.dto.AssistantEvent;
import com.healthassistant.assistant.api.dto.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/v1/assistant")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Assistant", description = "AI-powered health assistant for querying health data")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AssistantController {

    private final AssistantFacade assistantFacade;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Chat with AI health assistant (SSE streaming)",
            description = "Send a message to the AI assistant to query your health data. The AI will automatically fetch relevant data using available tools. Response is streamed via Server-Sent Events. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE stream started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Error processing chat request")
    })
    Flux<ServerSentEvent<String>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("SSE chat request from device {}: {}", deviceId, request.message());

        return assistantFacade.streamChat(request, deviceId)
                .map(this::toServerSentEvent)
                .doOnComplete(() -> logStreamCompleted(deviceId))
                .doOnError(error -> logStreamError(deviceId, error));
    }

    private ServerSentEvent<String> toServerSentEvent(AssistantEvent event) {
        try {
            return ServerSentEvent.<String>builder()
                    .event("message")
                    .data(serializeEvent(event))
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            throw new RuntimeException("Event serialization failed", e);
        }
    }

    private String serializeEvent(AssistantEvent event) throws Exception {
        return objectMapper.writeValueAsString(event);
    }

    private void logStreamCompleted(String deviceId) {
        log.info("SSE stream completed for device {}", deviceId);
    }

    private void logStreamError(String deviceId, Throwable error) {
        log.error("SSE stream failed for device {}", deviceId, error);
    }
}
