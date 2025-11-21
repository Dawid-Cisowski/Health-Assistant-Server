package com.healthassistant.dailysummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailySummaryService implements DailySummaryFacade {

    private final GenerateDailySummaryCommandHandler commandHandler;
    private final DailySummaryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public DailySummary generateDailySummary(LocalDate date) {
        return commandHandler.handle(GenerateDailySummaryCommand.forDate(date));
    }

    @Override
    public Optional<DailySummary> getDailySummary(LocalDate date) {
        return jpaRepository.findByDate(date)
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class));
    }
}
