package com.healthassistant.activity;

import com.healthassistant.activity.api.ActivityFacade;
import com.healthassistant.activity.api.dto.ActivityDailyBreakdownResponse;
import com.healthassistant.activity.api.dto.ActivityRangeSummaryResponse;
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
public class ActivityService implements ActivityFacade {

    private final ActivityDailyProjectionJpaRepository dailyRepository;
    private final ActivityHourlyProjectionJpaRepository hourlyRepository;

    @Override
    public ActivityDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date) {
        Optional<ActivityDailyBreakdownResponse> result = getDailyBreakdownInternal(deviceId, date);
        return result.orElseGet(() -> ActivityDailyBreakdownResponse.empty(date));
    }

    private Optional<ActivityDailyBreakdownResponse> getDailyBreakdownInternal(String deviceId, LocalDate date) {
        Optional<ActivityDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        ActivityDailyProjectionJpaEntity daily = dailyOpt.get();
        List<ActivityHourlyProjectionJpaEntity> hourlyData =
            hourlyRepository.findByDeviceIdAndDateOrderByHourAsc(deviceId, date);

        Map<Integer, Integer> hourlyMinutes = hourlyData.stream()
            .collect(Collectors.toMap(
                ActivityHourlyProjectionJpaEntity::getHour,
                ActivityHourlyProjectionJpaEntity::getActiveMinutes
            ));

        return Optional.of(ActivityDailyBreakdownResponse.of(
            daily.getDate(),
            daily.getTotalActiveMinutes(),
            daily.getFirstActivityTime(),
            daily.getLastActivityTime(),
            daily.getMostActiveHour(),
            daily.getMostActiveHourMinutes(),
            daily.getActiveHoursCount(),
            hourlyMinutes
        ));
    }

    @Override
    public ActivityRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<ActivityDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, ActivityDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(ActivityDailyProjectionJpaEntity::getDate, d -> d));

        List<ActivityRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            ActivityDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(new ActivityRangeSummaryResponse.DailyStats(
                current,
                dayData != null ? dayData.getTotalActiveMinutes() : 0,
                dayData != null ? dayData.getActiveHoursCount() : 0
            ));

            current = current.plusDays(1);
        }

        int totalMinutes = dailyStats.stream()
            .mapToInt(ActivityRangeSummaryResponse.DailyStats::totalActiveMinutes)
            .sum();

        int daysWithData = (int) dailyStats.stream()
            .filter(d -> d.totalActiveMinutes() > 0)
            .count();

        int totalDays = dailyStats.size();
        int averageMinutes = totalDays > 0 ? totalMinutes / totalDays : 0;

        return new ActivityRangeSummaryResponse(startDate, endDate, totalMinutes, averageMinutes, daysWithData, dailyStats);
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        hourlyRepository.deleteAll();
        dailyRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteProjectionsByDeviceId(String deviceId) {
        hourlyRepository.deleteByDeviceId(deviceId);
        dailyRepository.deleteByDeviceId(deviceId);
    }
}
