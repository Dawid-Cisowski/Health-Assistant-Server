package com.healthassistant.application.summary;

import com.healthassistant.domain.summary.DailySummary;

import java.time.LocalDate;
import java.util.Optional;

interface DailySummaryRepository {
    void save(DailySummary summary);

    Optional<DailySummary> findByDate(LocalDate date);
}

