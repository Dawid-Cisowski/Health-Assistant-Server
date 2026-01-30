package com.healthassistant.dailysummary;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    private final DailySummaryAggregator aggregator;
    private final DailySummaryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(GenerateDailySummaryCommand command) {
        log.info("Generating daily summary for device: {} date: {}", maskDeviceId(command.deviceId()), command.date());

        DailySummary summary = aggregator.aggregate(command.deviceId(), command.date());

        Map<String, Object> summaryMap = objectMapper.convertValue(summary, MAP_TYPE_REFERENCE);

        DailySummaryJpaEntity entity = jpaRepository.findByDeviceIdAndDate(command.deviceId(), summary.date())
                .map(existing -> {
                    existing.updateSummary(summaryMap);
                    return existing;
                })
                .orElseGet(() -> DailySummaryJpaEntity.builder()
                        .deviceId(command.deviceId())
                        .date(summary.date())
                        .summary(summaryMap)
                        .build());

        jpaRepository.save(entity);

        log.info("Daily summary generated and saved for device: {} date: {}", maskDeviceId(command.deviceId()), command.date());
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
