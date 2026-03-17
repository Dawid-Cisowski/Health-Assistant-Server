package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
class GoogleFitClient {

    private final RestClient restClient;

    GoogleFitClient(
            GoogleFitOAuthService oAuthService,
            @Value("${app.google-fit.api-url:https://www.googleapis.com/fitness/v1}") String apiUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(oAuthService.getAccessToken());
                    return execution.execute(request, body);
                })
                .build();
    }

    GoogleFitAggregateResponse fetchAggregated(AggregateRequest request) {
        return restClient.post()
                .uri("/users/me/dataset:aggregate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GoogleFitAggregateResponse.class);
    }

    GoogleFitSessionsResponse fetchSessions(String startTime, String endTime, boolean includeDeleted) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/me/sessions")
                        .queryParam("startTime", startTime)
                        .queryParam("endTime", endTime)
                        .queryParam("includeDeleted", includeDeleted)
                        .build())
                .retrieve()
                .body(GoogleFitSessionsResponse.class);
    }

    record AggregateRequest(
            @JsonProperty("aggregateBy") List<DataTypeAggregate> aggregateBy,
            @JsonProperty("bucketByTime") BucketByTime bucketByTime,
            @JsonProperty("startTimeMillis") Long startTimeMillis,
            @JsonProperty("endTimeMillis") Long endTimeMillis
    ) {}

    record DataTypeAggregate(
            @JsonProperty("dataTypeName") String dataTypeName
    ) {}

    record BucketByTime(
            @JsonProperty("durationMillis") Long durationMillis
    ) {}
}
