package com.healthassistant.infrastructure.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import feign.RequestInterceptor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "googleFitClient",
        url = "https://www.googleapis.com/fitness/v1",
        configuration = GoogleFitClient.FeignConfig.class
)
public interface GoogleFitClient {

    @PostMapping("/users/me/dataset:aggregate")
    GoogleFitAggregateResponse fetchAggregated(@RequestBody AggregateRequest request);

    @Data
    class AggregateRequest {
        @JsonProperty("aggregateBy")
        private List<DataTypeAggregate> aggregateBy;

        @JsonProperty("bucketByTime")
        private BucketByTime bucketByTime;

        @JsonProperty("startTimeMillis")
        private Long startTimeMillis;

        @JsonProperty("endTimeMillis")
        private Long endTimeMillis;
    }

    @Data
    class DataTypeAggregate {
        @JsonProperty("dataTypeName")
        private String dataTypeName;

        public DataTypeAggregate(String dataTypeName) {
            this.dataTypeName = dataTypeName;
        }
    }

    @Data
    class BucketByTime {
        @JsonProperty("durationMillis")
        private Long durationMillis;

        public BucketByTime(Long durationMillis) {
            this.durationMillis = durationMillis;
        }
    }

    @org.springframework.context.annotation.Configuration
    static class FeignConfig {
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
