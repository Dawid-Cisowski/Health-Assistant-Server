package com.healthassistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Temporary diagnostic endpoint to understand what Claude.ai sends to /register.
 * Claude.ai's MCP connector calls POST /register as part of its connection protocol.
 */
@RestController
@Slf4j
class McpRegisterController {

    @PostMapping("/register")
    ResponseEntity<Map<String, String>> register(
            @RequestBody(required = false) String body) {
        log.info("POST /register body: {}", body);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
