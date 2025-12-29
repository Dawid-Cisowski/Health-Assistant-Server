package com.healthassistant.steps.api;

import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;

import java.time.LocalDate;

public interface StepsFacade {

    StepsDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date);

    StepsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();
}
