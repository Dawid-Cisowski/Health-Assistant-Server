package com.healthassistant.steps.api;

import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;

import java.time.LocalDate;

public interface StepsFacade {

    StepsDailyBreakdownResponse getDailyBreakdown(LocalDate date);

    StepsRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate);
}
