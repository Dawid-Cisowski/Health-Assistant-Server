package com.healthassistant.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;

/**
 * Forwards POST /sse to POST /mcp (Spring AI Streamable HTTP transport).
 * Claude.ai sends POST /sse when using its native MCP connector (Streamable HTTP transport),
 * but Spring AI's Streamable HTTP RouterFunction registers at /mcp by default.
 */
@Controller
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
@Slf4j
class McpSseForwardController {

    @PostMapping("/sse")
    void forwardToMcp(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("MCP: forwarding POST /sse → /mcp");
        request.getRequestDispatcher("/mcp").forward(request, response);
    }
}
