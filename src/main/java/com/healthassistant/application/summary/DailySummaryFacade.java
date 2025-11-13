package com.healthassistant.application.summary;

import com.healthassistant.domain.summary.DailySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DailySummaryFacade {

    private final GenerateDailySummaryCommandHandler commandHandler;
    private final DailySummaryRepository repository;

    public DailySummary generateDailySummary(LocalDate date) {
        return commandHandler.handle(GenerateDailySummaryCommand.forDate(date));
    }

    public Optional<DailySummary> getDailySummary(LocalDate date) {
        return repository.findByDate(date);
    }
}

