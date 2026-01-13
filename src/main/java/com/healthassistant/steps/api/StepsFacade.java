package com.healthassistant.steps.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface StepsFacade {

    StepsDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date);

    StepsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);
}
