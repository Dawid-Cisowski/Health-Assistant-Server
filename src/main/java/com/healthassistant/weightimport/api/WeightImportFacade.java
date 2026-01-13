package com.healthassistant.weightimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.weightimport.api.dto.WeightImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface WeightImportFacade {

    WeightImportResponse importFromImage(MultipartFile image, DeviceId deviceId);
}
