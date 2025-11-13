package com.healthassistant.infrastructure.scheduler;

import com.healthassistant.application.summary.DailySummaryFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryScheduler {

    private final DailySummaryFacade dailySummaryFacade;

    @Scheduled(cron = "0 0 1 * * ?")
    public void generatePreviousDaySummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Scheduled task: Generating daily summary for {}", yesterday);
        
        try {
            dailySummaryFacade.generateDailySummary(yesterday);
            log.info("Successfully generated daily summary for {}", yesterday);
        } catch (Exception e) {
            log.error("Failed to generate daily summary for {}", yesterday, e);
        }
    }
}

