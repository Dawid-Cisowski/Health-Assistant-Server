package com.healthassistant.security;

import com.healthassistant.config.AppProperties;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.config.api.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "app.api-key.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String safePath = SecurityUtils.sanitizeForLog(path);

        if (!requiresAuthentication(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (token.isBlank()) {
            log.warn("API key authentication failed for path {}: empty token", safePath);
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "API_KEY_AUTH_FAILED", "Invalid authentication credentials");
            return;
        }

        byte[] providedHash = computeSha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] configuredHash = appProperties.getApiKey().getKeyHash();

        if (!MessageDigest.isEqual(configuredHash, providedHash)) {
            log.warn("API key authentication failed for path {}: invalid token", safePath);
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "API_KEY_AUTH_FAILED", "Invalid authentication credentials");
            return;
        }

        if (appProperties.getApiKey().isReadOnly() && MUTATION_METHODS.contains(request.getMethod())) {
            log.warn("API key read-only violation for path {} method {}", safePath, request.getMethod());
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "API_KEY_READ_ONLY", "This API key has read-only access");
            return;
        }

        String deviceId = appProperties.getApiKey().getDeviceId();
        request.setAttribute("deviceId", deviceId);
        log.debug("API key authentication successful, device: {}", SecurityUtils.maskDeviceId(deviceId));
        filterChain.doFilter(request, response);
    }

    /** Bearer header first, then ?token= query param (for MCP clients that can't set headers). */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return request.getParameter("token");
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
            || path.startsWith("/v1/exercises")
            || path.startsWith("/v1/reports")
            || path.startsWith("/v1/medical-exams")
            || path.equals("/sse");
    }

    private static byte[] computeSha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(code, message, List.of()));
    }
}
