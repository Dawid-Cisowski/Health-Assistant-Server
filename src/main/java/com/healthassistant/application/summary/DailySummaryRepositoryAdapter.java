package com.healthassistant.application.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthassistant.domain.summary.DailySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class DailySummaryRepositoryAdapter implements DailySummaryRepository {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final DailySummaryJpaRepository jpaRepository;

    @Override
    public void save(DailySummary summary) {
        Map<String, Object> summaryMap = objectMapper.convertValue(summary, Map.class);

        DailySummaryJpaEntity entity = jpaRepository.findByDate(summary.date())
                .map(existing -> updateExisting(existing, summaryMap))
                .orElseGet(() -> createNew(summary.date(), summaryMap));

        jpaRepository.save(entity);
    }

    private DailySummaryJpaEntity updateExisting(DailySummaryJpaEntity entity, Map<String, Object> summaryMap) {
        entity.setSummary(summaryMap);
        return entity;
    }

    private DailySummaryJpaEntity createNew(LocalDate date, Map<String, Object> summaryMap) {
        return DailySummaryJpaEntity.builder()
                .date(date)
                .summary(summaryMap)
                .build();
    }

    @Override
    public Optional<DailySummary> findByDate(LocalDate date) {
        return jpaRepository.findByDate(date)
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class));
    }

}

