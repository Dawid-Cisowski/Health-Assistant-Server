package com.healthassistant.activity.api;

import com.healthassistant.activity.api.dto.ActivityDailyBreakdownResponse;
import com.healthassistant.activity.api.dto.ActivityRangeSummaryResponse;
import com.healthassistant.healthevents.api.dto.StoredEventData;

import java.time.LocalDate;
import java.util.List;

public interface ActivityFacade {

    ActivityDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date);

    ActivityRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();

    void deleteProjectionsByDeviceId(String deviceId);

    void projectEvents(List<StoredEventData> events);
}
