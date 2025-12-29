package com.healthassistant.dailysummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class GenerateDailySummaryCommandHandler {

    private final DailySummaryAggregator aggregator;
    private final DailySummaryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(GenerateDailySummaryCommand command) {
        log.info("Generating daily summary for device: {} date: {}", command.deviceId(), command.date());

        DailySummary summary = aggregator.aggregate(command.deviceId(), command.date());

        Map<String, Object> summaryMap = objectMapper.convertValue(summary, Map.class);

        DailySummaryJpaEntity entity = jpaRepository.findByDate(summary.date())
                .map(existing -> {
                    existing.setSummary(summaryMap);
                    return existing;
                })
                .orElseGet(() -> DailySummaryJpaEntity.builder()
                        .date(summary.date())
                        .summary(summaryMap)
                        .build());

        jpaRepository.save(entity);

        log.info("Daily summary generated and saved for device: {} date: {}", command.deviceId(), command.date());
    }
}
