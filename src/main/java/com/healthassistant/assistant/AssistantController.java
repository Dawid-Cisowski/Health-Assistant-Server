package com.healthassistant.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.assistant.api.AssistantFacade;
import com.healthassistant.assistant.api.dto.AssistantEvent;
import com.healthassistant.assistant.api.dto.ChatRequest;
import com.healthassistant.assistant.api.dto.ErrorEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/v1/assistant")
@Slf4j
@Tag(name = "AI Assistant", description = "AI-powered health assistant for querying health data")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AssistantController {

    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

    private final AssistantFacade assistantFacade;
    private final ObjectMapper objectMapper;
    private final int maxRequestsPerMinute;
    private final ConcurrentMap<String, RateLimitBucket> rateLimitBuckets = new ConcurrentHashMap<>();

    AssistantController(
            AssistantFacade assistantFacade,
            ObjectMapper objectMapper,
            @Value("${app.assistant.rate-limit.max-requests-per-minute:10}") int maxRequestsPerMinute
    ) {
        this.assistantFacade = assistantFacade;
        this.objectMapper = objectMapper;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

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
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Error processing chat request")
    })
    Flux<ServerSentEvent<String>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("SSE chat request from device {}: {}", deviceId, request.message());

        if (!tryAcquireRateLimit(deviceId)) {
            log.warn("Rate limit exceeded for device {}", deviceId);
            return createRateLimitExceededResponse();
        }

        return assistantFacade.streamChat(request, deviceId)
                .map(this::serializeToSseEvent)
                .doOnComplete(() -> log.info("SSE stream completed for device {}", deviceId))
                .doOnError(error -> log.error("SSE stream failed for device {}", deviceId, error));
    }

    private ServerSentEvent<String> serializeToSseEvent(AssistantEvent event) {
        try {
            return ServerSentEvent.<String>builder()
                    .event("message")
                    .data(objectMapper.writeValueAsString(event))
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize event: {}", event.getClass().getSimpleName(), e);
            return createErrorSseEvent("Internal error occurred while processing response.");
        }
    }

    private ServerSentEvent<String> createErrorSseEvent(String message) {
        try {
            var errorEvent = new ErrorEvent(message);
            return ServerSentEvent.<String>builder()
                    .event("message")
                    .data(objectMapper.writeValueAsString(errorEvent))
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize error event", e);
            return ServerSentEvent.<String>builder()
                    .event("message")
                    .data("{\"type\":\"error\",\"content\":\"Internal error\"}")
                    .build();
        }
    }

    private Flux<ServerSentEvent<String>> createRateLimitExceededResponse() {
        return Flux.just(createErrorSseEvent("Rate limit exceeded. Please wait a moment before trying again."));
    }

    private boolean tryAcquireRateLimit(String deviceId) {
        if (maxRequestsPerMinute <= 0) {
            return true;
        }

        var now = Instant.now().toEpochMilli();
        var bucket = rateLimitBuckets.compute(deviceId, (key, existing) -> {
            if (existing == null || now - existing.windowStart > RATE_LIMIT_WINDOW_MS) {
                return new RateLimitBucket(now, new AtomicInteger(1));
            }
            existing.requestCount.incrementAndGet();
            return existing;
        });

        return bucket.requestCount.get() <= maxRequestsPerMinute;
    }

    private record RateLimitBucket(long windowStart, AtomicInteger requestCount) {}
}
