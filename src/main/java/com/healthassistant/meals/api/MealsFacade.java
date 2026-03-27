package com.healthassistant.meals.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.mealcatalog.api.CatalogSortOrder;
import com.healthassistant.mealcatalog.api.dto.CatalogProductResponse;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import com.healthassistant.meals.api.dto.RecordMealFromCatalogRequest;
import com.healthassistant.meals.api.dto.RecordMealRequest;
import com.healthassistant.meals.api.dto.UpdateMealRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealsFacade {

    MealDailyDetailResponse getDailyDetail(String deviceId, LocalDate date);

    MealsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    MealResponse recordMeal(String deviceId, RecordMealRequest request);

    void deleteMeal(String deviceId, String eventId);

    MealResponse updateMeal(String deviceId, String eventId, UpdateMealRequest request);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);

    Optional<EnergyRequirementsResponse> getEnergyRequirements(String deviceId, LocalDate date);

    List<CatalogProductResponse> browseCatalog(String deviceId, String query, CatalogSortOrder sortOrder, int limit);

    MealResponse recordMealFromCatalog(String deviceId, RecordMealFromCatalogRequest request);
}
