package com.healthassistant.calories.api;

import com.healthassistant.calories.api.dto.CaloriesDailyBreakdownResponse;
import com.healthassistant.calories.api.dto.CaloriesRangeSummaryResponse;

import java.time.LocalDate;

public interface CaloriesFacade {

    CaloriesDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date);

    CaloriesRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();
}
