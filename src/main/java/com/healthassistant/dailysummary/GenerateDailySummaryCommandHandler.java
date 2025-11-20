package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
class GenerateDailySummaryCommandHandler {

    private final DailySummaryAggregator aggregator;
    private final DailySummaryRepository repository;

    @Transactional
    public DailySummary handle(GenerateDailySummaryCommand command) {
        log.info("Generating daily summary for date: {}", command.date());

        DailySummary summary = aggregator.aggregate(command.date());
        repository.save(summary);

        log.info("Daily summary generated and saved for date: {}", command.date());
        return summary;
    }
}
