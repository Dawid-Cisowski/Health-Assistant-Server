package com.healthassistant.mealcatalog.api;

import com.healthassistant.mealcatalog.api.dto.CatalogProductResponse;
import com.healthassistant.mealcatalog.api.dto.SaveProductRequest;

import java.util.List;

public interface MealCatalogFacade {

    void saveProduct(String deviceId, SaveProductRequest request);

    List<CatalogProductResponse> searchProducts(String deviceId, String query, int maxResults);

    List<CatalogProductResponse> getTopProducts(String deviceId, int limit);
}
