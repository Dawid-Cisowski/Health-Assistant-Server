package com.healthassistant.mealimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MealImportFacade {

    /**
     * Import a meal from description and/or images.
     * AI analyzes the content and estimates nutritional values.
     *
     * @param description optional text description of the meal
     * @param images optional list of meal photos (at least description or images required)
     * @param deviceId the device making the request
     * @return import result with extracted meal data or error
     */
    MealImportResponse importMeal(String description, List<MultipartFile> images, DeviceId deviceId);
}
