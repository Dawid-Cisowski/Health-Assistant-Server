package com.healthassistant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.config.AppProperties;
import com.healthassistant.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Filter that validates HMAC authentication on protected endpoints
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HmacAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_DEVICE_ID = "X-Device-Id";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HEADER_CONTENT_SHA256 = "Content-SHA256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String NONCE_CACHE = "nonces";

    private final AppProperties appProperties;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Only protect /v1/ingest/* endpoints
        if (!request.getRequestURI().startsWith("/v1/ingest/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request to allow multiple reads of the body
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        try {
            validateHmacAuthentication(wrappedRequest);
            
            // Store device ID in request attribute for use in controller
            wrappedRequest.setAttribute("deviceId", wrappedRequest.getHeader(HEADER_DEVICE_ID));
            
            filterChain.doFilter(wrappedRequest, response);
        } catch (HmacAuthenticationException e) {
            log.warn("HMAC authentication failed: {}", e.getMessage());
            sendError(response, HttpStatus.UNAUTHORIZED, "HMAC_AUTH_FAILED", e.getMessage());
        }
    }

    private void validateHmacAuthentication(CachedBodyHttpServletRequest request) throws HmacAuthenticationException, IOException {
        // Extract headers
        String deviceId = request.getHeader(HEADER_DEVICE_ID);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);
        log.info("HMAC authentication headers: deviceId={}, timestamp={}, nonce={}, signature={}", deviceId, timestampStr, nonce, signature);


        // Validate presence of required headers
        if (deviceId == null || deviceId.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Device-Id header");
        }
        if (timestampStr == null || timestampStr.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Timestamp header");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Nonce header");
        }
        if (signature == null || signature.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Signature header");
        }

        // Validate device ID and get secret
        byte[] secret = appProperties.getHmac().getDeviceSecrets().get(deviceId);
        if (secret == null) {
            throw new HmacAuthenticationException("Unknown device ID: " + deviceId);
        }

        // Validate timestamp
        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampStr);
        } catch (DateTimeParseException e) {
            throw new HmacAuthenticationException("Invalid timestamp format (expected ISO-8601 UTC)");
        }

        Instant now = Instant.now();
        long toleranceSeconds = appProperties.getHmac().getToleranceSeconds();
        if (timestamp.isBefore(now.minusSeconds(toleranceSeconds)) || 
            timestamp.isAfter(now.plusSeconds(toleranceSeconds))) {
            throw new HmacAuthenticationException("Timestamp out of acceptable range (tolerance: " + toleranceSeconds + "s)");
        }

        // Validate nonce (anti-replay)
        Cache nonceCache = cacheManager.getCache(NONCE_CACHE);
        if (nonceCache != null) {
            String cacheKey = deviceId + ":" + nonce;
            if (nonceCache.get(cacheKey) != null) {
                throw new HmacAuthenticationException("Nonce already used (replay attack detected)");
            }
            nonceCache.put(cacheKey, true);
        }

        // Read body from cache
        byte[] body = request.getCachedBody();

        // Build canonical string
        String method = request.getMethod();
        String path = request.getRequestURI();
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        String canonicalString = String.join("\n",
            method,
            path,
            timestampStr,
            nonce,
            deviceId,
            bodyStr
        );

        // Log components for debugging
        log.info("=== HMAC Signature Verification (Backend) ===");
        log.info("Method: {}", method);
        log.info("Path: {}", path);
        log.info("Timestamp: {}", timestampStr);
        log.info("Nonce: {}", nonce);
        log.info("Device ID: {}", deviceId);
        log.info("Body length: {}", bodyStr.length());
        log.info("Body: {}", bodyStr);
        log.info("Secret length: {} bytes", secret.length);
        log.info("---");
        log.info("Canonical string ({} chars):", canonicalString.length());
        log.info("{}", canonicalString);
        log.info("---");

        // Calculate expected HMAC
        String expectedSignature;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret, HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            expectedSignature = Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new HmacAuthenticationException("Failed to compute HMAC: " + e.getMessage());
        }

        log.info("Expected signature: {}", expectedSignature);
        log.info("Received signature: {}", signature);
        log.info("============================================");

        // Compare signatures (constant-time comparison)
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new HmacAuthenticationException("Invalid signature");
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code(code)
            .message(message)
            .details(Collections.emptyList())
            .build();
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    public static class HmacAuthenticationException extends Exception {
        public HmacAuthenticationException(String message) {
            super(message);
        }
    }
}

