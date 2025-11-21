package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthassistant.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
class GoogleFitOAuthService {

    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.create();
    private AccessTokenCache tokenCache;
    
    private String getTokenUrl() {
        String oauthUrl = appProperties.getGoogleFit().getOauthUrl();
        return oauthUrl != null ? oauthUrl + "/token" : "https://oauth2.googleapis.com/token";
    }

    public String getAccessToken() {
        if (tokenCache != null && tokenCache.isValid()) {
            return tokenCache.accessToken;
        }

        log.debug("Fetching new access token from Google OAuth");
        TokenResponse response = refreshAccessToken();
        
        tokenCache = new AccessTokenCache(
                response.accessToken,
                Instant.now().plusSeconds(response.expiresIn - 60) // Refresh 1 minute before expiry
        );

        return response.accessToken;
    }

    private TokenResponse refreshAccessToken() {
        AppProperties.GoogleFitConfig config = appProperties.getGoogleFit();
        if (config.getClientId() == null || config.getClientSecret() == null || config.getRefreshToken() == null) {
            throw new IllegalStateException("Google Fit OAuth credentials not configured");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());
        body.add("refresh_token", config.getRefreshToken());

        TokenResponse response = restClient.post()
                .uri(getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken == null) {
            throw new IllegalStateException("Failed to obtain access token from Google OAuth");
        }

        log.info("Successfully obtained Google Fit access token");
        return response;
    }

    private record TokenResponse(
            @JsonProperty("access_token")
            String accessToken,

            @JsonProperty("expires_in")
            Integer expiresIn,

            @JsonProperty("token_type")
            String tokenType
    ) {
    }

    private record AccessTokenCache(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return expiresAt != null && expiresAt.isAfter(Instant.now());
        }
    }
}

