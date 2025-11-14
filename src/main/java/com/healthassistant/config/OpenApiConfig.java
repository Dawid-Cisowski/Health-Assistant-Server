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
        title = "Health Assistant API",
        version = "1.0.0",
        description = """
            # Health Assistant API
            
            RESTful API for health data synchronization and daily summaries.
            Events are automatically synchronized from Google Fit API and aggregated into daily summaries.
            
            ## Features
            
            - **Google Fit Integration**: Automatic synchronization of health data from Google Fit
            - **Daily Summaries**: Aggregated health metrics per day
            - **Append-Only Storage**: Events stored in PostgreSQL with JSONB payloads
            - **HMAC Authentication**: Secure header-based authentication for mobile app access
            """
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local development"),
        @Server(url = "https://health-assistant-event-collector-289829161711.europe-central2.run.app", description = "Production")
    }
)
@SecurityScheme(
    name = "HmacHeaderAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-Signature",
    description = """
        ## HMAC-SHA256 Authentication
        
        All requests to `/v1/daily-summaries/*` require HMAC-SHA256 authentication.
        
        ### Required Headers
        
        | Header | Type | Description | Example |
        |--------|------|-------------|---------|
        | `X-Device-Id` | string | Unique device identifier | `device-abc123` |
        | `X-Timestamp` | string | Current time in ISO-8601 UTC format | `2025-11-09T07:05:12Z` |
        | `X-Nonce` | string | Random UUID (lowercase) | `550e8400-e29b-41d4-a716-446655440000` |
        | `X-Signature` | string | Base64-encoded HMAC-SHA256 signature | `dGVzdC1zaWduYXR1cmU=` |
        
        ### Signature Calculation (GET requests)
        
        For GET requests, the canonical string is:
        
        ```
        GET
        /v1/daily-summaries/2025-11-10
        2025-11-09T07:05:12Z
        550e8400-e29b-41d4-a716-446655440000
        device-abc123
        
        ```
        
        Note: Empty body for GET requests.
        
        **Steps:**
        1. Concatenate the above lines with newline characters (`\\n`)
        2. Compute HMAC-SHA256 using your device secret (base64-decoded)
        3. Base64-encode the HMAC result
        4. Set as `X-Signature` header value
        
        ### Timestamp Tolerance
        
        Timestamps must be within ±10 minutes (600 seconds) of the server's current time.
        
        ### Nonce Replay Protection
        
        Each nonce can only be used once per device. Nonces are cached for 10 minutes.
        """
)
class OpenApiConfig {
}

