package com.healthassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Daily health summary")
public class DailySummaryResponse {

    @JsonProperty("date")
    @Schema(description = "Date for which the summary is generated (YYYY-MM-DD)", example = "2025-11-12")
    private LocalDate date;

    @JsonProperty("activity")
    @Schema(description = "Daily activity metrics")
    private Activity activity;

    @JsonProperty("workouts")
    @Schema(description = "List of workouts performed during the day")
    private List<Workout> workouts;

    @JsonProperty("sleep")
    @Schema(description = "Sleep session information")
    private Sleep sleep;

    @JsonProperty("heart")
    @Schema(description = "Heart rate metrics")
    private Heart heart;

    @JsonProperty("score")
    @Schema(description = "Health scores")
    private Score score;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Daily activity metrics")
    public static class Activity {
        @JsonProperty("steps")
        @Schema(description = "Total steps", example = "15631", nullable = true)
        private Integer steps;

        @JsonProperty("activeMinutes")
        @Schema(description = "Total active minutes", example = "87", nullable = true)
        private Integer activeMinutes;

        @JsonProperty("activeCalories")
        @Schema(description = "Total active calories burned", example = "560", nullable = true)
        private Integer activeCalories;

        @JsonProperty("distanceMeters")
        @Schema(description = "Total distance in meters", example = "7100.0", nullable = true)
        private Double distanceMeters;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Workout session")
    public static class Workout {
        @JsonProperty("type")
        @Schema(description = "Workout type (e.g., WALK, running, cycling)", example = "WALK")
        private String type;

        @JsonProperty("start")
        @Schema(description = "Workout start time (ISO-8601 UTC)", example = "2025-11-12T17:20:00Z")
        private Instant start;

        @JsonProperty("end")
        @Schema(description = "Workout end time (ISO-8601 UTC)", example = "2025-11-12T17:55:00Z")
        private Instant end;

        @JsonProperty("durationMinutes")
        @Schema(description = "Workout duration in minutes", example = "35", nullable = true)
        private Integer durationMinutes;

        @JsonProperty("distanceMeters")
        @Schema(description = "Distance covered in meters", example = "5200.0", nullable = true)
        private Double distanceMeters;

        @JsonProperty("avgHr")
        @Schema(description = "Average heart rate in BPM", example = "141", nullable = true)
        private Integer avgHr;

        @JsonProperty("energyKcal")
        @Schema(description = "Calories burned during workout", example = "320", nullable = true)
        private Integer energyKcal;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Sleep session information")
    public static class Sleep {
        @JsonProperty("start")
        @Schema(description = "Sleep start time (ISO-8601 UTC)", example = "2025-11-11T23:26:00Z", nullable = true)
        private Instant start;

        @JsonProperty("end")
        @Schema(description = "Sleep end time (ISO-8601 UTC)", example = "2025-11-12T06:00:00Z", nullable = true)
        private Instant end;

        @JsonProperty("totalMinutes")
        @Schema(description = "Total sleep duration in minutes", example = "394", nullable = true)
        private Integer totalMinutes;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Heart rate metrics")
    public static class Heart {
        @JsonProperty("restingBpm")
        @Schema(description = "Resting heart rate in BPM", example = "62", nullable = true)
        private Integer restingBpm;

        @JsonProperty("avgBpm")
        @Schema(description = "Average heart rate in BPM", example = "74", nullable = true)
        private Integer avgBpm;

        @JsonProperty("maxBpm")
        @Schema(description = "Maximum heart rate in BPM", example = "138", nullable = true)
        private Integer maxBpm;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Health scores")
    public static class Score {
        @JsonProperty("activityScore")
        @Schema(description = "Activity score (0-100)", example = "82")
        private Integer activityScore;

        @JsonProperty("sleepScore")
        @Schema(description = "Sleep score (0-100)", example = "76")
        private Integer sleepScore;

        @JsonProperty("readinessScore")
        @Schema(description = "Readiness score (0-100)", example = "78")
        private Integer readinessScore;

        @JsonProperty("overallScore")
        @Schema(description = "Overall health score (0-100)", example = "79")
        private Integer overallScore;
    }
}

