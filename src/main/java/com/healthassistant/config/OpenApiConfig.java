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
            # Health Events Ingestion API
            
            RESTful API for storing normalized health events from mobile applications. 
            Designed for high-throughput, append-only event storage with HMAC authentication and idempotency guarantees.
            
            ## Features
            
            - **Batch Processing**: Accept up to 100 events per request
            - **Idempotency**: Automatic deduplication using client-provided keys
            - **Append-Only Storage**: Events stored in PostgreSQL with JSONB payloads
            - **Type-Specific Validation**: Each event type has its own payload schema
            - **HMAC Authentication**: Secure header-based authentication with replay protection
            
            ## Supported Event Types
            
            1. **StepsBucketedRecorded.v1** - Step count for a time bucket
               - Tracks steps taken within a specific time window
               - Fields: `bucketStart`, `bucketEnd`, `count`, `originPackage`
            
            2. **HeartRateSummaryRecorded.v1** - Heart rate summary for a time bucket
               - Provides average, min, max heart rate and sample count
               - Fields: `bucketStart`, `bucketEnd`, `avg`, `min`, `max`, `samples`, `originPackage`
            
            3. **SleepSessionRecorded.v1** - Sleep session with start/end times
               - Tracks sleep duration and timing
               - Fields: `sleepStart`, `sleepEnd`, `totalMinutes`, `originPackage`
            
            4. **ActiveCaloriesBurnedRecorded.v1** - Active calories burned in a time bucket
               - Tracks calories burned from physical activity
               - Fields: `bucketStart`, `bucketEnd`, `energyKcal`, `originPackage`
            
            5. **ActiveMinutesRecorded.v1** - Active minutes in a time bucket
               - Tracks moderate and vigorous activity minutes
               - Fields: `bucketStart`, `bucketEnd`, `activeMinutes`, `originPackage`
            
            ## Authentication
            
            All requests require HMAC-SHA256 authentication via custom headers:
            - **X-Device-Id**: Unique device identifier (string)
            - **X-Timestamp**: Current time in ISO-8601 UTC format (e.g., `2025-11-09T07:05:12Z`)
            - **X-Nonce**: Random UUID to prevent replay attacks
            - **X-Signature**: Base64-encoded HMAC-SHA256 signature
            
            See the security scheme below for detailed signature calculation.
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
        
        All API requests must be authenticated using HMAC-SHA256 signatures calculated over a canonical string.
        
        ### Required Headers
        
        | Header | Type | Description | Example |
        |--------|------|-------------|---------|
        | `X-Device-Id` | string | Unique device identifier registered with the server | `device-abc123` |
        | `X-Timestamp` | string | Current time in ISO-8601 UTC format | `2025-11-09T07:05:12Z` |
        | `X-Nonce` | string | Random UUID (lowercase) to prevent replay attacks | `550e8400-e29b-41d4-a716-446655440000` |
        | `X-Signature` | string | Base64-encoded HMAC-SHA256 signature | `dGVzdC1zaWduYXR1cmU=` |
        
        ### Signature Calculation
        
        The signature is calculated over a canonical string constructed as follows:
        
        ```
        METHOD
        PATH
        X-Timestamp
        X-Nonce
        X-Device-Id
        REQUEST_BODY
        ```
        
        **Example:**
        
        ```
        POST
        /v1/health-events
        2025-11-09T07:05:12Z
        550e8400-e29b-41d4-a716-446655440000
        device-abc123
        {"events":[...]}
        ```
        
        **Steps:**
        1. Concatenate the above lines with newline characters (`\\n`)
        2. Compute HMAC-SHA256 using your device secret (base64-decoded)
        3. Base64-encode the HMAC result
        4. Set as `X-Signature` header value
        
        ### Timestamp Tolerance
        
        Timestamps must be within ±10 minutes (600 seconds) of the server's current time. 
        Requests with timestamps outside this window will be rejected.
        
        ### Nonce Replay Protection
        
        Each nonce can only be used once per device. Reusing a nonce will result in authentication failure.
        Nonces are cached for 10 minutes (600 seconds) to prevent replay attacks.
        """
)
class OpenApiConfig {
}

