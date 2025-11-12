package com.healthassistant.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Health Assistant – Health Events API",
        version = "1.0.0",
        description = """
            Store normalized health events from the mobile app.
            Auth: HMAC via headers (see securitySchemes).
            
            Required headers for authenticated requests:
            - X-Device-Id: string
            - X-Timestamp: RFC3339 UTC (e.g., 2025-11-09T07:05:12Z)
            - X-Nonce: random UUID
            - X-Signature: HMAC-SHA256 base64 over canonical string
            """
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local development"),
        @Server(url = "https://api.example.com", description = "Production")
    }
)
@SecurityScheme(
    name = "HmacHeaderAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-Signature",
    description = """
        HMAC-SHA256 authentication using custom headers.
        
        Required headers:
        - X-Device-Id: Unique device identifier
        - X-Timestamp: Current time in ISO-8601 UTC format
        - X-Nonce: Random UUID to prevent replay attacks
        - X-Signature: Base64-encoded HMAC-SHA256 signature
        
        Canonical string format (joined by newlines):
        METHOD
        PATH
        X-Timestamp
        X-Nonce
        X-Device-Id
        REQUEST_BODY
        """
)
public class OpenApiConfig {
}

