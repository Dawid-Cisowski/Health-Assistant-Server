package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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

    @GetMapping("/{date}")
    @Operation(
            summary = "Get daily summary for a specific date",
            description = "Retrieves the daily summary for the specified date if it exists. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
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
