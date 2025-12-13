package com.healthassistant.sleep.api;

import com.healthassistant.sleep.api.dto.SleepDailyDetailResponse;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;

import java.time.LocalDate;

public interface SleepFacade {

    SleepDailyDetailResponse getDailyDetail(LocalDate date);

    SleepRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();
}
