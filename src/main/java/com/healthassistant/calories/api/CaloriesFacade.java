package com.healthassistant.calories.api;

import com.healthassistant.calories.api.dto.CaloriesDailyBreakdownResponse;
import com.healthassistant.calories.api.dto.CaloriesRangeSummaryResponse;
import com.healthassistant.healthevents.api.dto.StoredEventData;

import java.time.LocalDate;
import java.util.List;

public interface CaloriesFacade {

    CaloriesDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date);

    CaloriesRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);
}
