package com.healthassistant.dailysummary.api;

import com.healthassistant.dailysummary.api.dto.DailySummary;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySummaryFacade {

    DailySummary generateDailySummary(LocalDate date);

    Optional<DailySummary> getDailySummary(LocalDate date);
}
