package com.healthassistant.infrastructure.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.application.authentication.port.DeviceSecretProvider;
import com.healthassistant.application.authentication.port.NonceCache;
import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
class HmacAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_DEVICE_ID = "X-Device-Id";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";

    private final DeviceSecretProvider deviceSecretProvider;
    private final NonceCache nonceCache;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/v1/health-events")) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        try {
            DeviceId deviceId = validateHmacAuthentication(wrappedRequest);
            wrappedRequest.setAttribute("deviceId", deviceId.value());
            filterChain.doFilter(wrappedRequest, response);
        } catch (HmacAuthenticationException e) {
            log.warn("HMAC authentication failed: {}", e.getMessage());
            sendError(response, e.getMessage());
        }
    }

    private DeviceId validateHmacAuthentication(CachedBodyHttpServletRequest request)
            throws HmacAuthenticationException {

        String deviceIdStr = request.getHeader(HEADER_DEVICE_ID);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);

        validateHeadersPresent(deviceIdStr, timestampStr, nonce, signature);

        DeviceId deviceId = DeviceId.of(deviceIdStr);
        byte[] secret = deviceSecretProvider.getSecret(deviceId)
                .orElseThrow(() -> new HmacAuthenticationException("Unknown device ID: " + deviceId.value()));

        Instant timestamp = parseTimestamp(timestampStr);
        validateTimestamp(timestamp);

        if (nonceCache.isUsed(deviceId, nonce)) {
            throw new HmacAuthenticationException("Nonce already used (replay attack detected)");
        }

        byte[] body = request.getCachedBody();
        String bodyStr = new String(body, StandardCharsets.UTF_8);

        String expectedSignature = HmacSignature.calculate(
                request.getMethod(),
                request.getRequestURI(),
                timestampStr,
                nonce,
                deviceId,
                bodyStr,
                secret
        );

        if (!HmacSignature.verify(expectedSignature, signature)) {
            throw new HmacAuthenticationException("Invalid signature");
        }

        nonceCache.markAsUsed(deviceId, nonce);
        return deviceId;
    }

    private void validateHeadersPresent(String deviceId, String timestamp, String nonce, String signature)
            throws HmacAuthenticationException {
        if (deviceId == null || deviceId.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Device-Id header");
        }
        if (timestamp == null || timestamp.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Timestamp header");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Nonce header");
        }
        if (signature == null || signature.isBlank()) {
            throw new HmacAuthenticationException("Missing X-Signature header");
        }
    }

    private Instant parseTimestamp(String timestampStr) throws HmacAuthenticationException {
        try {
            return Instant.parse(timestampStr);
        } catch (DateTimeParseException e) {
            throw new HmacAuthenticationException("Invalid timestamp format (expected ISO-8601 UTC)");
        }
    }

    private void validateTimestamp(Instant timestamp) throws HmacAuthenticationException {
        Instant now = Instant.now();
        long toleranceSeconds = 600;
        if (timestamp.isBefore(now.minusSeconds(toleranceSeconds)) ||
                timestamp.isAfter(now.plusSeconds(toleranceSeconds))) {
            throw new HmacAuthenticationException("Timestamp out of acceptable range (tolerance: " + toleranceSeconds + "s)");
        }
    }

    private void sendError(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("HMAC_AUTH_FAILED")
                .message(message)
                .details(Collections.emptyList())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    static class HmacAuthenticationException extends Exception {
        HmacAuthenticationException(String message) {
            super(message);
        }
    }
}

