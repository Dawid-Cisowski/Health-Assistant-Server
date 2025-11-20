package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.dto.DailySummary;

import java.time.LocalDate;
import java.util.Optional;

interface DailySummaryRepository {
    void save(DailySummary summary);

    Optional<DailySummary> findByDate(LocalDate date);
}
