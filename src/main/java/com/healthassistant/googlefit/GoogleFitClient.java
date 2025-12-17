package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "googleFitClient",
        url = "${app.google-fit.api-url:https://www.googleapis.com/fitness/v1}",
        configuration = GoogleFitClient.FeignConfig.class
)
 interface GoogleFitClient {

    @PostMapping("/users/me/dataset:aggregate")
    GoogleFitAggregateResponse fetchAggregated(@RequestBody AggregateRequest request);

    @GetMapping("/users/me/sessions")
    GoogleFitSessionsResponse fetchSessions(
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted
    );

    record AggregateRequest(
            @JsonProperty("aggregateBy")
            List<DataTypeAggregate> aggregateBy,

            @JsonProperty("bucketByTime")
            BucketByTime bucketByTime,

            @JsonProperty("startTimeMillis")
            Long startTimeMillis,

            @JsonProperty("endTimeMillis")
            Long endTimeMillis
    ) {
    }

    record DataTypeAggregate(
            @JsonProperty("dataTypeName")
            String dataTypeName
    ) {
    }

    record BucketByTime(
            @JsonProperty("durationMillis")
            Long durationMillis
    ) {
    }

    @Configuration
    class FeignConfig {
        private final GoogleFitOAuthService oAuthService;

        FeignConfig(GoogleFitOAuthService oAuthService) {
            this.oAuthService = oAuthService;
        }

        @Bean
        public RequestInterceptor requestInterceptor() {
            return requestTemplate -> {
                String accessToken = oAuthService.getAccessToken();
                requestTemplate.header("Authorization", "Bearer " + accessToken);
            };
        }
    }
}
