package com.healthassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.AbstractIntegrationTest;
import com.healthassistant.dto.EventEnvelope;
import com.healthassistant.dto.IngestRequest;
import com.healthassistant.dto.IngestResponse;
import com.healthassistant.repository.HealthEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for event ingestion endpoint
 */
class IngestControllerIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HealthEventRepository eventRepository;

    private String baseUrl;
    private static final String DEVICE_ID = "test-device";
    private static final String SECRET = "test-secret-123";
    private static final byte[] SECRET_BYTES = Base64.getDecoder().decode("dGVzdC1zZWNyZXQtMTIz");

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
    }

    @Test
    void testIngestEvents_Success() throws Exception {
        // Prepare request
        EventEnvelope event = EventEnvelope.builder()
            .idempotencyKey("test-user|healthconnect|steps|" + System.currentTimeMillis())
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "count", 742,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        IngestRequest request = IngestRequest.builder()
            .events(List.of(event))
            .build();

        // Send request with HMAC authentication
        ResponseEntity<IngestResponse> response = sendAuthenticatedRequest(request, IngestResponse.class);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResults()).hasSize(1);
        
        IngestResponse.EventResult result = response.getBody().getResults().get(0);
        assertThat(result.getStatus()).isEqualTo(IngestResponse.EventStatus.stored);
        assertThat(result.getEventId()).isNotNull();
        assertThat(result.getEventId()).startsWith("evt_");

        // Verify event was stored in database
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventRepository.existsByIdempotencyKey(event.getIdempotencyKey())).isTrue();
    }

    @Test
    void testIngestEvents_Duplicate() throws Exception {
        String idempotencyKey = "test-user|healthconnect|steps|duplicate-test";
        
        EventEnvelope event = EventEnvelope.builder()
            .idempotencyKey(idempotencyKey)
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "count", 742,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        IngestRequest request = IngestRequest.builder()
            .events(List.of(event))
            .build();

        // Send first request
        ResponseEntity<IngestResponse> response1 = sendAuthenticatedRequest(request, IngestResponse.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response1.getBody().getResults().get(0).getStatus()).isEqualTo(IngestResponse.EventStatus.stored);

        // Send duplicate request
        ResponseEntity<IngestResponse> response2 = sendAuthenticatedRequest(request, IngestResponse.class);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response2.getBody().getResults().get(0).getStatus()).isEqualTo(IngestResponse.EventStatus.duplicate);

        // Verify only one event was stored
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void testIngestEvents_InvalidEventType() throws Exception {
        EventEnvelope event = EventEnvelope.builder()
            .idempotencyKey("test-user|healthconnect|invalid|" + System.currentTimeMillis())
            .type("InvalidEventType.v1")
            .occurredAt(Instant.now())
            .payload(Map.of("test", "data"))
            .build();

        IngestRequest request = IngestRequest.builder()
            .events(List.of(event))
            .build();

        ResponseEntity<IngestResponse> response = sendAuthenticatedRequest(request, IngestResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getResults().get(0).getStatus()).isEqualTo(IngestResponse.EventStatus.invalid);
        assertThat(eventRepository.count()).isEqualTo(0);
    }

    @Test
    void testIngestEvents_MissingHmacHeaders() throws Exception {
        EventEnvelope event = EventEnvelope.builder()
            .idempotencyKey("test-user|healthconnect|steps|" + System.currentTimeMillis())
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "count", 742,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        IngestRequest request = IngestRequest.builder()
            .events(List.of(event))
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<IngestRequest> httpEntity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/v1/ingest/events",
            HttpMethod.POST,
            httpEntity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testIngestEvents_InvalidSignature() throws Exception {
        EventEnvelope event = EventEnvelope.builder()
            .idempotencyKey("test-user|healthconnect|steps|" + System.currentTimeMillis())
            .type("StepsBucketedRecorded.v1")
            .occurredAt(Instant.now())
            .payload(Map.of(
                "bucketStart", "2025-11-09T06:00:00Z",
                "bucketEnd", "2025-11-09T07:00:00Z",
                "count", 742,
                "originPackage", "com.heytap.health.international"
            ))
            .build();

        IngestRequest request = IngestRequest.builder()
            .events(List.of(event))
            .build();

        String timestamp = Instant.now().toString();
        String nonce = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", DEVICE_ID);
        headers.set("X-Timestamp", timestamp);
        headers.set("X-Nonce", nonce);
        headers.set("X-Signature", "invalid-signature");

        HttpEntity<IngestRequest> httpEntity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/v1/ingest/events",
            HttpMethod.POST,
            httpEntity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testIngestEvents_BatchProcessing() throws Exception {
        String baseKey = "test-user|healthconnect|steps|batch-" + System.currentTimeMillis();
        
        IngestRequest request = IngestRequest.builder()
            .events(List.of(
                EventEnvelope.builder()
                    .idempotencyKey(baseKey + "-1")
                    .type("StepsBucketedRecorded.v1")
                    .occurredAt(Instant.now())
                    .payload(Map.of(
                        "bucketStart", "2025-11-09T06:00:00Z",
                        "bucketEnd", "2025-11-09T07:00:00Z",
                        "count", 742,
                        "originPackage", "com.heytap.health.international"
                    ))
                    .build(),
                EventEnvelope.builder()
                    .idempotencyKey(baseKey + "-2")
                    .type("HeartRateSummaryRecorded.v1")
                    .occurredAt(Instant.now())
                    .payload(Map.of(
                        "bucketStart", "2025-11-09T07:00:00Z",
                        "bucketEnd", "2025-11-09T07:15:00Z",
                        "avg", 78.3,
                        "min", 61,
                        "max", 115,
                        "samples", 46,
                        "originPackage", "com.heytap.health.international"
                    ))
                    .build()
            ))
            .build();

        ResponseEntity<IngestResponse> response = sendAuthenticatedRequest(request, IngestResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getResults()).hasSize(2);
        assertThat(response.getBody().getResults()).allMatch(
            r -> r.getStatus() == IngestResponse.EventStatus.stored
        );
        assertThat(eventRepository.count()).isEqualTo(2);
    }

    private <T> ResponseEntity<T> sendAuthenticatedRequest(IngestRequest request, Class<T> responseType) throws Exception {
        String timestamp = Instant.now().toString();
        String nonce = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(request);

        // Build canonical string
        String canonicalString = String.join("\n",
            "POST",
            "/v1/ingest/events",
            timestamp,
            nonce,
            DEVICE_ID,
            body
        );

        // Calculate HMAC
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_BYTES, "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(hmacBytes);

        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", DEVICE_ID);
        headers.set("X-Timestamp", timestamp);
        headers.set("X-Nonce", nonce);
        headers.set("X-Signature", signature);

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);

        return restTemplate.exchange(
            baseUrl + "/v1/ingest/events",
            HttpMethod.POST,
            httpEntity,
            responseType
        );
    }
}

