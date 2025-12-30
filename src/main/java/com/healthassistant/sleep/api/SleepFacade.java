package com.healthassistant.sleep.api;

import com.healthassistant.sleep.api.dto.SleepDailyDetailResponse;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;

import java.time.LocalDate;

public interface SleepFacade {

    SleepDailyDetailResponse getDailyDetail(String deviceId, LocalDate date);

    SleepRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();

    void deleteProjectionsByDeviceId(String deviceId);

    /**
     * Rebuilds sleep projections from existing events for a specific device.
     * Useful for repairing projections that were not created due to duplicate event handling.
     *
     * @param deviceId the device ID to rebuild projections for
     * @return the number of projections rebuilt
     */
    int rebuildProjections(String deviceId);
}
