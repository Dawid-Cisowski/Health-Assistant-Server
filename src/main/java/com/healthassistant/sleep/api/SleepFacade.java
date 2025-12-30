package com.healthassistant.sleep.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.sleep.api.dto.SleepDailyDetailResponse;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface SleepFacade {

    SleepDailyDetailResponse getDailyDetail(String deviceId, LocalDate date);

    SleepRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();

    void deleteProjectionsByDeviceId(String deviceId);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    /**
     * Rebuilds sleep projections from existing events for a specific device.
     * Useful for repairing projections that were not created due to duplicate event handling.
     *
     * @param deviceId the device ID to rebuild projections for
     * @return the number of projections rebuilt
     */
    int rebuildProjections(String deviceId);

    void projectEvents(List<StoredEventData> events);
}
