package com.healthassistant.sleep.api;

import com.healthassistant.sleep.api.dto.SleepDailyDetailResponse;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;

import java.time.LocalDate;

public interface SleepFacade {

    SleepDailyDetailResponse getDailyDetail(String deviceId, LocalDate date);

    SleepRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();

    void deleteProjectionsByDeviceId(String deviceId);
}
