package com.healthassistant.workoutimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.workoutimport.api.dto.WorkoutImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface WorkoutImportFacade {

    WorkoutImportResponse importFromImage(MultipartFile image, DeviceId deviceId);
}
