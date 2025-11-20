package com.healthassistant.security;

import com.healthassistant.config.api.dto.ErrorResponse;
import com.healthassistant.security.api.DeviceSecretProvider;
import com.healthassistant.security.api.NonceCache;
import com.healthassistant.config.AppProperties;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
@Slf4j
class HmacAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceSecretProvider deviceSecretProvider;
    private final NonceCache nonceCache;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/v1/daily-summaries")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            DeviceId deviceId = validateHmacAuthentication(request);
            request.setAttribute("deviceId", deviceId.value());
            filterChain.doFilter(request, response);
        } catch (HmacAuthenticationException e) {
            log.warn("HMAC authentication failed: {}", e.getMessage());
            sendErrorResponse(response, e.getMessage());
        }
    }

    private DeviceId validateHmacAuthentication(HttpServletRequest request)
            throws HmacAuthenticationException {
        String deviceIdStr = extractHeader(request, "X-Device-Id");
        String timestampStr = extractHeader(request, "X-Timestamp");
        String nonce = extractHeader(request, "X-Nonce");
        String signature = extractHeader(request, "X-Signature");

        DeviceId deviceId = DeviceId.of(deviceIdStr);
        byte[] secret = deviceSecretProvider.getSecret(deviceId.value())
                .orElseThrow(() -> new HmacAuthenticationException("Unknown device ID: " + deviceId.value()));

        if (nonceCache.isUsed(deviceId.value(), nonce)) {
            throw new HmacAuthenticationException("Nonce already used (replay attack detected)");
        }

        Instant timestamp = parseTimestamp(timestampStr);
        validateTimestamp(timestamp);

        String method = request.getMethod();
        String path = request.getRequestURI();
        String body = getRequestBody(request);

        String canonicalString = buildCanonicalString(method, path, timestampStr, nonce, deviceId.value(), body);
        String expectedSignature = HmacSignature.calculate(canonicalString, secret);

        if (!HmacSignature.verify(expectedSignature, signature)) {
            throw new HmacAuthenticationException("Invalid signature");
        }

        nonceCache.markAsUsed(deviceId.value(), nonce);
        return deviceId;
    }

    private String extractHeader(HttpServletRequest request, String headerName)
            throws HmacAuthenticationException {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            throw new HmacAuthenticationException("Missing " + headerName + " header");
        }
        return value;
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
        long toleranceSeconds = appProperties.getHmac().getToleranceSeconds();
        long diffSeconds = Math.abs(now.getEpochSecond() - timestamp.getEpochSecond());

        if (diffSeconds > toleranceSeconds) {
            throw new HmacAuthenticationException("Timestamp out of acceptable range (tolerance: " + toleranceSeconds + "s)");
        }
    }

    private String buildCanonicalString(String method, String path, String timestamp, String nonce, String deviceId, String body) {
        return String.join("\n", method, path, timestamp, nonce, deviceId, body != null ? body : "");
    }

    private String getRequestBody(HttpServletRequest request) {
        return "";
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("HMAC_AUTH_FAILED")
                .message(message)
                .details(java.util.List.of())
                .build();

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    static class HmacAuthenticationException extends Exception {
        HmacAuthenticationException(String message) {
            super(message);
        }
    }
}
