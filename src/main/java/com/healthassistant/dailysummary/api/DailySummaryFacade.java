package com.healthassistant.dailysummary.api;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySummaryFacade {

    void generateDailySummary(String deviceId, LocalDate date);

    Optional<DailySummary> getDailySummary(String deviceId, LocalDate date);

    DailySummaryRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllSummaries();

    void deleteSummariesByDeviceId(String deviceId);

    void deleteSummaryForDate(String deviceId, LocalDate date);
}
