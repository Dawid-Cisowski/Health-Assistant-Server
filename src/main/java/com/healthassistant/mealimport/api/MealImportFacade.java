package com.healthassistant.mealimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MealImportFacade {

    MealImportResponse importMeal(String description, List<MultipartFile> images, DeviceId deviceId);
}
