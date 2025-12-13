package com.healthassistant.steps;

import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StepsService implements StepsFacade {

    private final StepsDailyProjectionJpaRepository dailyRepository;
    private final StepsHourlyProjectionJpaRepository hourlyRepository;

    @Override
    public StepsDailyBreakdownResponse getDailyBreakdown(LocalDate date) {
        Optional<StepsDailyBreakdownResponse> result = getDailyBreakdownInternal(date);
        return result.orElseGet(() -> createEmptyBreakdown(date));
    }

    private Optional<StepsDailyBreakdownResponse> getDailyBreakdownInternal(LocalDate date) {
        Optional<StepsDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDate(date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        StepsDailyProjectionJpaEntity daily = dailyOpt.get();
        List<StepsHourlyProjectionJpaEntity> hourlyData =
            hourlyRepository.findByDateOrderByHourAsc(date);

        Map<Integer, Integer> hourlySteps = hourlyData.stream()
            .collect(Collectors.toMap(
                StepsHourlyProjectionJpaEntity::getHour,
                StepsHourlyProjectionJpaEntity::getStepCount
            ));

        List<StepsDailyBreakdownResponse.HourlySteps> hourlyBreakdown = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourlyBreakdown.add(new StepsDailyBreakdownResponse.HourlySteps(
                hour,
                hourlySteps.getOrDefault(hour, 0)
            ));
        }

        return Optional.of(StepsDailyBreakdownResponse.builder()
            .date(daily.getDate())
            .totalSteps(daily.getTotalSteps())
            .firstStepTime(daily.getFirstStepTime())
            .lastStepTime(daily.getLastStepTime())
            .mostActiveHour(daily.getMostActiveHour())
            .mostActiveHourSteps(daily.getMostActiveHourSteps())
            .activeHoursCount(daily.getActiveHoursCount())
            .hourlyBreakdown(hourlyBreakdown)
            .build());
    }

    private StepsDailyBreakdownResponse createEmptyBreakdown(LocalDate date) {
        List<StepsDailyBreakdownResponse.HourlySteps> hourlyBreakdown = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourlyBreakdown.add(new StepsDailyBreakdownResponse.HourlySteps(hour, 0));
        }

        return StepsDailyBreakdownResponse.builder()
            .date(date)
            .totalSteps(0)
            .firstStepTime(null)
            .lastStepTime(null)
            .mostActiveHour(null)
            .mostActiveHourSteps(0)
            .activeHoursCount(0)
            .hourlyBreakdown(hourlyBreakdown)
            .build();
    }

    /**
     * Get range summary (daily totals for specified date range)
     * Client decides if it's week (7 days), month (~30 days), or year (365 days)
     */
    @Override
    public StepsRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate) {
        List<StepsDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);

        Map<LocalDate, StepsDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(StepsDailyProjectionJpaEntity::getDate, d -> d));

        List<StepsRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            StepsDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(StepsRangeSummaryResponse.DailyStats.builder()
                .date(current)
                .totalSteps(dayData != null ? dayData.getTotalSteps() : 0)
                .activeHoursCount(dayData != null ? dayData.getActiveHoursCount() : 0)
                .build());

            current = current.plusDays(1);
        }

        int totalSteps = dailyStats.stream()
            .mapToInt(StepsRangeSummaryResponse.DailyStats::totalSteps)
            .sum();

        int daysWithData = (int) dailyStats.stream()
            .filter(d -> d.totalSteps() > 0)
            .count();

        int totalDays = dailyStats.size();
        int averageSteps = totalDays > 0 ? totalSteps / totalDays : 0;

        return StepsRangeSummaryResponse.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalSteps(totalSteps)
            .averageSteps(averageSteps)
            .daysWithData(daysWithData)
            .dailyStats(dailyStats)
            .build();
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all steps projections");
        hourlyRepository.deleteAll();
        dailyRepository.deleteAll();
    }
}
