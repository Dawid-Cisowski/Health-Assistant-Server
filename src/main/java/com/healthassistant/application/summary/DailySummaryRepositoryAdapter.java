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
        
        Optional<DailySummaryJpaEntity> existing = jpaRepository.findByDate(summary.date());

        DailySummaryJpaEntity entity;

        if (existing.isPresent()) {
            entity = existing.get();
            entity.setSummary(summaryMap);
        } else {
            entity = DailySummaryJpaEntity.builder()
                    .date(summary.date())
                    .summary(summaryMap)
                    .build();
        }
        jpaRepository.save(entity);
    }

    @Override
    public Optional<DailySummary> findByDate(LocalDate date) {
        return jpaRepository.findByDate(date)
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class));
    }

}

