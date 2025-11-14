package com.healthassistant.infrastructure.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class GoogleFitAggregateResponse {
    @JsonProperty("bucket")
    private List<GoogleFitBucket> buckets;

    public GoogleFitAggregateResponse() {
        this.buckets = new ArrayList<>();
    }

    public GoogleFitAggregateResponse(List<GoogleFitBucket> buckets) {
        this.buckets = buckets != null ? buckets : new ArrayList<>();
    }

    @Data
    public static class GoogleFitBucket {
        @JsonProperty("startTimeMillis")
        private Long startTimeMillis;
        
        @JsonProperty("endTimeMillis")
        private Long endTimeMillis;
        
        @JsonProperty("dataset")
        private List<Dataset> datasets;

        @Data
        public static class Dataset {
            @JsonProperty("dataSourceId")
            private String dataSourceId;
            
            @JsonProperty("point")
            private List<DataPoint> points;

            @Data
            public static class DataPoint {
                @JsonProperty("startTimeNanos")
                private String startTimeNanos;
                
                @JsonProperty("endTimeNanos")
                private String endTimeNanos;
                
                @JsonProperty("value")
                private List<Value> values;

                @Data
                public static class Value {
                    @JsonProperty("intVal")
                    private Long intVal;
                    
                    @JsonProperty("fpVal")
                    private Double fpVal;
                    
                    @JsonProperty("mapVal")
                    private List<MapValueEntry> mapVal;
                    
                    @Data
                    public static class MapValueEntry {
                        @JsonProperty("key")
                        private String key;
                        
                        @JsonProperty("value")
                        private MapValue value;
                    }
                    
                    @Data
                    public static class MapValue {
                        @JsonProperty("intVal")
                        private Long intVal;
                        
                        @JsonProperty("fpVal")
                        private Double fpVal;
                    }
                }
            }
        }
    }
}

