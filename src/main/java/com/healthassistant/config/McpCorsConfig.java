package com.healthassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for MCP endpoints (/sse and /mcp/*).
 * Required so Claude.ai (browser-side) can connect to the MCP server.
 */
@Configuration
class McpCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/sse")
                .allowedOrigins("https://claude.ai")
                .allowedMethods("GET")
                .allowedHeaders("*")
                .allowCredentials(false);

        registry.addMapping("/mcp/**")
                .allowedOrigins("https://claude.ai")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);

        registry.addMapping("/.well-known/**")
                .allowedOrigins("https://claude.ai")
                .allowedMethods("GET")
                .allowedHeaders("*")
                .allowCredentials(false);

        registry.addMapping("/register")
                .allowedOrigins("https://claude.ai")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);

        registry.addMapping("/authorize")
                .allowedOrigins("https://claude.ai")
                .allowedMethods("GET")
                .allowedHeaders("*")
                .allowCredentials(false);

        registry.addMapping("/token")
                .allowedOrigins("https://claude.ai")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
