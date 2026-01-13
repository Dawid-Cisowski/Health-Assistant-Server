package com.healthassistant.heartrate.api;

import com.healthassistant.heartrate.api.dto.HeartRateRangeResponse;
import com.healthassistant.heartrate.api.dto.RestingHeartRateResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HeartRateFacade {

    HeartRateRangeResponse getRange(String deviceId, LocalDate from, LocalDate to);

    List<RestingHeartRateResponse> getRestingRange(String deviceId, LocalDate from, LocalDate to);

    Optional<Integer> getRestingBpmForDate(String deviceId, LocalDate date);

    void deleteProjectionsForDate(String deviceId, LocalDate date);
}
