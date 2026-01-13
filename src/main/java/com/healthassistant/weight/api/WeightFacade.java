package com.healthassistant.weight.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.weight.api.dto.WeightLatestResponse;
import com.healthassistant.weight.api.dto.WeightMeasurementResponse;
import com.healthassistant.weight.api.dto.WeightRangeSummaryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightFacade {

    Optional<WeightLatestResponse> getLatestMeasurement(String deviceId);

    WeightRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    Optional<WeightMeasurementResponse> getMeasurementById(String deviceId, String measurementId);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);
}
