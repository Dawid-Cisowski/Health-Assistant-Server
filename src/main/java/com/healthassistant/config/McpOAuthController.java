package com.healthassistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal OAuth 2.0 authorization server for Claude.ai MCP connector.
 * <p>
 * Claude.ai's MCP connector requires OAuth 2.0 (RFC 7591 Dynamic Client Registration +
 * RFC 8414 Authorization Server Metadata). This controller auto-approves all requests
 * and issues the configured CLAUDE_API_KEY as the access token, so the existing
 * ApiKeyAuthenticationFilter continues to validate MCP requests.
 * <p>
 * Flow: discovery → register → authorize (auto-redirect) → token exchange → MCP access
 */
@RestController
@RequiredArgsConstructor
@Slf4j
class McpOAuthController {

    private final AppProperties appProperties;

    @Value("${MCP_BASE_URL:http://localhost:8080}")
    private String baseUrl;

    // Single-use auth codes: code → apiKey (cleared after token exchange)
    private final Map<String, String> pendingCodes = new ConcurrentHashMap<>();

    // ── RFC 9728 — OAuth 2.0 Protected Resource Metadata ────────────────────
    @GetMapping("/.well-known/oauth-protected-resource")
    Map<String, Object> protectedResourceMetadata() {
        log.debug("MCP OAuth: protected resource metadata request");
        return Map.of(
                "resource", baseUrl,
                "authorization_servers", List.of(baseUrl),
                "scopes_supported", List.of("claudeai"),
                "bearer_methods_supported", List.of("header", "query")
        );
    }

    // ── RFC 8414 — Authorization Server Metadata ────────────────────────────
    @GetMapping("/.well-known/oauth-authorization-server")
    Map<String, Object> oauthMetadata() {
        log.debug("MCP OAuth: metadata discovery from {}", baseUrl);
        return Map.of(
                "issuer", baseUrl,
                "authorization_endpoint", baseUrl + "/authorize",
                "token_endpoint", baseUrl + "/token",
                "registration_endpoint", baseUrl + "/register",
                "scopes_supported", List.of("claudeai"),
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of("authorization_code", "refresh_token"),
                "token_endpoint_auth_methods_supported", List.of("client_secret_post", "none"),
                "code_challenge_methods_supported", List.of("S256", "plain")
        );
    }

    // ── RFC 7591 — Dynamic Client Registration ───────────────────────────────
    @PostMapping("/register")
    ResponseEntity<Map<String, Object>> registerClient(@RequestBody Map<String, Object> body) {
        log.info("MCP OAuth: Claude.ai client registration");
        Map<String, Object> response = new HashMap<>(body);
        response.put("client_id", "claude-mcp-" + UUID.randomUUID());
        response.put("client_secret", UUID.randomUUID().toString());
        response.put("client_id_issued_at", Instant.now().getEpochSecond());
        response.put("client_secret_expires_at", 0);
        return ResponseEntity.status(201).body(response);
    }

    // ── Authorization endpoint — auto-approves, immediate redirect ────────────
    @GetMapping("/authorize")
    ResponseEntity<Void> authorize(
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("state") String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod) {

        log.info("MCP OAuth: auto-approving authorization request");
        String apiKey = appProperties.getApiKey().getKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("MCP OAuth: CLAUDE_API_KEY not configured");
            return ResponseEntity.badRequest().build();
        }

        String code = UUID.randomUUID().toString();
        pendingCodes.put(code, apiKey);

        String location = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .queryParam("state", state)
                .toUriString();

        log.info("MCP OAuth: redirecting to Claude.ai callback with code");
        return ResponseEntity.status(302).header("Location", location).build();
    }

    // ── Token endpoint — exchanges code for CLAUDE_API_KEY ───────────────────
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Map<String, Object>> exchangeToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier) {

        log.info("MCP OAuth: token exchange, grant_type={}", grantType);
        String apiKey = resolveApiKey(grantType, code, refreshToken);

        if (apiKey == null) {
            log.warn("MCP OAuth: invalid grant");
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
        }

        return ResponseEntity.ok(Map.of(
                "access_token", apiKey,
                "token_type", "Bearer",
                "expires_in", 86400,
                "refresh_token", apiKey,
                "scope", "claudeai"
        ));
    }

    private String resolveApiKey(String grantType, String code, String refreshToken) {
        return switch (grantType) {
            case "authorization_code" -> code != null ? pendingCodes.remove(code) : null;
            case "refresh_token" -> refreshToken;
            default -> null;
        };
    }
}
