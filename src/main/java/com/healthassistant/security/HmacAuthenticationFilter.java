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
import java.util.Objects;

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

        if (!requiresAuthentication(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest wrappedRequest = request;
        String method = request.getMethod();
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.contains("multipart/form-data");
        if (hasRequestBody(method) && !isMultipart) {
            wrappedRequest = CachedBodyHttpServletRequest.of(request);
        }

        try {
            DeviceId deviceId = validateHmacAuthentication(wrappedRequest);
            wrappedRequest.setAttribute("deviceId", deviceId.value());
            filterChain.doFilter(wrappedRequest, response);
        } catch (HmacAuthenticationException e) {
            log.warn("HMAC authentication failed for path {}: {}", path, e.getMessage());
            sendErrorResponse(response, e.getMessage());
        }
    }

    private boolean hasRequestBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private boolean requiresAuthentication(String path) {
        return path.startsWith("/v1/health-events")
            || path.startsWith("/v1/daily-summaries")
            || path.startsWith("/v1/google-fit/sync")
            || path.startsWith("/v1/steps")
            || path.startsWith("/v1/workouts")
            || path.startsWith("/v1/meals")
            || path.startsWith("/v1/sleep")
            || path.startsWith("/v1/weight")
            || path.startsWith("/v1/admin")
            || path.startsWith("/v1/assistant")
            || path.startsWith("/v1/routines")
            || path.startsWith("/v1/exercises");
    }

    private DeviceId validateHmacAuthentication(HttpServletRequest request)
            throws HmacAuthenticationException {
        String deviceIdStr = extractHeader(request, "X-Device-Id");
        String timestampStr = extractHeader(request, "X-Timestamp");
        String nonce = extractHeader(request, "X-Nonce");
        String signature = extractHeader(request, "X-Signature");

        DeviceId deviceId = DeviceId.of(deviceIdStr);
        byte[] secret = deviceSecretProvider.getSecret(deviceId.value())
                .orElseThrow(() -> new HmacAuthenticationException("Invalid authentication credentials"));

        if (!nonceCache.markAsUsedIfAbsent(deviceId.value(), nonce)) {
            throw new HmacAuthenticationException("Nonce already used (replay attack detected)");
        }

        Instant timestamp = parseTimestamp(timestampStr);
        validateTimestamp(timestamp);

        String method = request.getMethod();
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            path = path + "?" + queryString;
        }
        String body = getRequestBody(request);

        String canonicalString = buildCanonicalString(method, path, timestampStr, nonce, deviceId.value(), body);
        String expectedSignature = HmacSignature.calculate(canonicalString, secret);

        if (!HmacSignature.verify(expectedSignature, signature)) {
            throw new HmacAuthenticationException("Invalid signature");
        }

        return deviceId;
    }

    private String extractHeader(HttpServletRequest request, String headerName)
            throws HmacAuthenticationException {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            throw new HmacAuthenticationException("Missing " + headerName + " header");
        }

        if (value.contains("\n") || value.contains("\r")) {
            throw new HmacAuthenticationException("Invalid character in " + headerName + " header");
        }

        return value;
    }

    private Instant parseTimestamp(String timestampStr) throws HmacAuthenticationException {
        try {
            return Instant.parse(timestampStr);
        } catch (DateTimeParseException e) {
            throw new HmacAuthenticationException("Invalid timestamp format (expected ISO-8601 UTC)", e);
        }
    }

    private void validateTimestamp(Instant timestamp) throws HmacAuthenticationException {
        Instant now = Instant.now();
        long toleranceSeconds = appProperties.getHmac().getToleranceSeconds();
        long diffSeconds = Math.abs(now.getEpochSecond() - timestamp.getEpochSecond());

        if (diffSeconds > toleranceSeconds) {
            throw new HmacAuthenticationException("Timestamp out of acceptable range");
        }
    }

    private String buildCanonicalString(String method, String path, String timestamp, String nonce, String deviceId, String body) {
        return String.join("\n", method, path, timestamp, nonce, deviceId, body != null ? body : "");
    }

    private String getRequestBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("multipart/form-data")) {
            return "";
        }

        if (request instanceof CachedBodyHttpServletRequest cachedRequest) {
            return cachedRequest.getBody();
        }
        return "";
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String safeMessage = Objects.requireNonNullElse(message, "Authentication failed");
        ErrorResponse errorResponse = new ErrorResponse("HMAC_AUTH_FAILED", safeMessage, java.util.List.of());

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    static class HmacAuthenticationException extends Exception {
        private static final long serialVersionUID = 1L;

        HmacAuthenticationException(String message) {
            super(message);
        }

        HmacAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
