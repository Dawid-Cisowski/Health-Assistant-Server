package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public record GoogleFitAggregateResponse(
        @JsonProperty("bucket")
        List<GoogleFitBucket> buckets
) {
    public GoogleFitAggregateResponse {
        buckets = buckets != null ? buckets : new ArrayList<>();
    }

    public record GoogleFitBucket(
            @JsonProperty("startTimeMillis")
            Long startTimeMillis,

            @JsonProperty("endTimeMillis")
            Long endTimeMillis,

            @JsonProperty("dataset")
            List<Dataset> datasets
    ) {
        public record Dataset(
                @JsonProperty("dataSourceId")
                String dataSourceId,

                @JsonProperty("point")
                List<DataPoint> points
        ) {
            public record DataPoint(
                    @JsonProperty("startTimeNanos")
                    String startTimeNanos,

                    @JsonProperty("endTimeNanos")
                    String endTimeNanos,

                    @JsonProperty("value")
                    List<Value> values
            ) {
                public record Value(
                        @JsonProperty("intVal")
                        Long intVal,

                        @JsonProperty("fpVal")
                        Double fpVal,

                        @JsonProperty("mapVal")
                        List<MapValueEntry> mapVal
                ) {
                    public record MapValueEntry(
                            @JsonProperty("key")
                            String key,

                            @JsonProperty("value")
                            MapValue value
                    ) {
                    }

                    public record MapValue(
                            @JsonProperty("intVal")
                            Long intVal,

                            @JsonProperty("fpVal")
                            Double fpVal
                    ) {
                    }
                }
            }
        }
    }
}

