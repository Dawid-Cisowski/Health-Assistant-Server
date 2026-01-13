package com.healthassistant.weightimport.api;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.weightimport.api.dto.WeightImportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface WeightImportFacade {

    WeightImportResponse importFromImages(List<MultipartFile> images, DeviceId deviceId);
}
