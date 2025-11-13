package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.summary.DailySummaryFacade;
import com.healthassistant.dto.DailySummaryResponse;
import com.healthassistant.infrastructure.web.rest.mapper.DailySummaryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/daily-summaries")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Daily Summaries", description = "Daily health summaries aggregated from events")
class DailySummaryController {

    private final DailySummaryFacade dailySummaryFacade;

    @PostMapping("/generate")
    @Operation(
            summary = "Trigger scheduled daily summary generation",
            description = """
                    Triggers the daily summary generation task. This endpoint is intended to be called 
                    by Google Cloud Scheduler or similar cron services.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary generation triggered successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<Void> triggerScheduledGeneration() {
        log.info("Triggering scheduled daily summary generation");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        dailySummaryFacade.generateDailySummary(yesterday);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/generate/{date}")
    @Operation(
            summary = "Manually generate daily summary for a specific date",
            description = """
                    Manually generates a daily summary for the specified date. If a summary already exists 
                    for this date, it will be overwritten.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary generated successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<DailySummaryResponse> generateForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Manually generating daily summary for date: {}", date);
        var summary = dailySummaryFacade.generateDailySummary(date);
        return ResponseEntity.ok(DailySummaryMapper.toResponse(summary));
    }

    @GetMapping("/{date}")
    @Operation(
            summary = "Get daily summary for a specific date",
            description = "Retrieves the daily summary for the specified date if it exists."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary found"),
            @ApiResponse(responseCode = "404", description = "Summary not found for the specified date")
    })
    ResponseEntity<DailySummaryResponse> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Retrieving daily summary for date: {}", date);
        return dailySummaryFacade.getDailySummary(date)
                .map(summary -> ResponseEntity.ok(DailySummaryMapper.toResponse(summary)))
                .orElse(ResponseEntity.notFound().build());
    }
}

