package com.healthassistant.sleepimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.sleepimport.api.dto.SleepImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SleepImportFacade {

    SleepImportResponse importFromImage(MultipartFile image, DeviceId deviceId, Integer year);
}
