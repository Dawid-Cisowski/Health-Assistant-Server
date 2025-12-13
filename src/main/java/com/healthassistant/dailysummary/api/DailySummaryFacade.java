package com.healthassistant.dailysummary.api;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySummaryFacade {

    void generateDailySummary(LocalDate date);

    Optional<DailySummary> getDailySummary(LocalDate date);

    DailySummaryRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate);

    void deleteAllSummaries();
}
