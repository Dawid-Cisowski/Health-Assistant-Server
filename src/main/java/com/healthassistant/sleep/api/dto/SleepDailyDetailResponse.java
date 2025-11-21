package com.healthassistant.sleep.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "Daily sleep detail with all sleep sessions for dashboard view")
public record SleepDailyDetailResponse(
    @JsonProperty("date")
    @Schema(description = "Date for the sleep data", example = "2025-11-19")
    LocalDate date,

    @JsonProperty("totalSleepMinutes")
    @Schema(description = "Total sleep minutes for the day (all sessions)", example = "480")
    Integer totalSleepMinutes,

    @JsonProperty("sleepCount")
    @Schema(description = "Number of sleep sessions (main sleep + naps)", example = "2")
    Integer sleepCount,

    @JsonProperty("firstSleepStart")
    @Schema(description = "Time when first sleep started", example = "2025-11-18T22:30:00Z")
    Instant firstSleepStart,

    @JsonProperty("lastSleepEnd")
    @Schema(description = "Time when last sleep ended", example = "2025-11-19T07:15:00Z")
    Instant lastSleepEnd,

    @JsonProperty("longestSessionMinutes")
    @Schema(description = "Duration of longest sleep session", example = "420")
    Integer longestSessionMinutes,

    @JsonProperty("shortestSessionMinutes")
    @Schema(description = "Duration of shortest sleep session", example = "60")
    Integer shortestSessionMinutes,

    @JsonProperty("averageSessionMinutes")
    @Schema(description = "Average session duration", example = "240")
    Integer averageSessionMinutes,

    @JsonProperty("totalLightSleepMinutes")
    @Schema(description = "Total light sleep minutes (future support)", example = "180")
    Integer totalLightSleepMinutes,

    @JsonProperty("totalDeepSleepMinutes")
    @Schema(description = "Total deep sleep minutes (future support)", example = "120")
    Integer totalDeepSleepMinutes,

    @JsonProperty("totalRemSleepMinutes")
    @Schema(description = "Total REM sleep minutes (future support)", example = "90")
    Integer totalRemSleepMinutes,

    @JsonProperty("totalAwakeMinutes")
    @Schema(description = "Total awake minutes during sleep (future support)", example = "30")
    Integer totalAwakeMinutes,

    @JsonProperty("sessions")
    @Schema(description = "Individual sleep sessions for the day")
    List<SleepSession> sessions
) {
    @Builder
    public record SleepSession(
        @JsonProperty("sessionNumber")
        @Schema(description = "Session number (1=main sleep, 2+=naps)", example = "1")
        Integer sessionNumber,

        @JsonProperty("sleepStart")
        @Schema(description = "When sleep started", example = "2025-11-18T22:30:00Z")
        Instant sleepStart,

        @JsonProperty("sleepEnd")
        @Schema(description = "When sleep ended", example = "2025-11-19T06:30:00Z")
        Instant sleepEnd,

        @JsonProperty("durationMinutes")
        @Schema(description = "Duration in minutes", example = "420")
        Integer durationMinutes,

        @JsonProperty("lightSleepMinutes")
        @Schema(description = "Light sleep phase minutes (future support)", example = "150")
        Integer lightSleepMinutes,

        @JsonProperty("deepSleepMinutes")
        @Schema(description = "Deep sleep phase minutes (future support)", example = "100")
        Integer deepSleepMinutes,

        @JsonProperty("remSleepMinutes")
        @Schema(description = "REM sleep phase minutes (future support)", example = "80")
        Integer remSleepMinutes,

        @JsonProperty("awakeMinutes")
        @Schema(description = "Awake minutes during session (future support)", example = "20")
        Integer awakeMinutes
    ) {}
}
