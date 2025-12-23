package com.healthassistant.mealimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.dto.MealDraftResponse;
import com.healthassistant.mealimport.api.dto.MealDraftUpdateRequest;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MealImportFacade {

    MealImportResponse importMeal(String description, List<MultipartFile> images, DeviceId deviceId);

    MealDraftResponse analyzeMeal(String description, List<MultipartFile> images, DeviceId deviceId);

    MealDraftResponse updateDraft(UUID draftId, MealDraftUpdateRequest request, DeviceId deviceId);

    MealImportResponse confirmDraft(UUID draftId, DeviceId deviceId);
}
