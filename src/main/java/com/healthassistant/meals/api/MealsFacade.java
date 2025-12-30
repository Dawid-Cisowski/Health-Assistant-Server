package com.healthassistant.meals.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface MealsFacade {

    MealDailyDetailResponse getDailyDetail(String deviceId, LocalDate date);

    MealsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();

    void deleteProjectionsByDeviceId(String deviceId);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);
}
