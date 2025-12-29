package com.healthassistant.meals.api;

import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;

import java.time.LocalDate;

public interface MealsFacade {

    MealDailyDetailResponse getDailyDetail(String deviceId, LocalDate date);

    MealsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();
}
