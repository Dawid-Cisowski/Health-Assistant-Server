package com.healthassistant.calories;

import com.healthassistant.calories.api.CaloriesFacade;
import com.healthassistant.calories.api.dto.CaloriesDailyBreakdownResponse;
import com.healthassistant.calories.api.dto.CaloriesRangeSummaryResponse;
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
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class CaloriesService implements CaloriesFacade {

    private final CaloriesDailyProjectionJpaRepository dailyRepository;
    private final CaloriesHourlyProjectionJpaRepository hourlyRepository;

    @Override
    public CaloriesDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date) {
        Optional<CaloriesDailyBreakdownResponse> result = getDailyBreakdownInternal(deviceId, date);
        return result.orElseGet(() -> createEmptyBreakdown(date));
    }

    private Optional<CaloriesDailyBreakdownResponse> getDailyBreakdownInternal(String deviceId, LocalDate date) {
        Optional<CaloriesDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        CaloriesDailyProjectionJpaEntity daily = dailyOpt.get();
        List<CaloriesHourlyProjectionJpaEntity> hourlyData =
            hourlyRepository.findByDeviceIdAndDateOrderByHourAsc(deviceId, date);

        Map<Integer, Double> hourlyCalories = hourlyData.stream()
            .collect(Collectors.toMap(
                CaloriesHourlyProjectionJpaEntity::getHour,
                CaloriesHourlyProjectionJpaEntity::getCaloriesKcal
            ));

        List<CaloriesDailyBreakdownResponse.HourlyCalories> hourlyBreakdown = IntStream.range(0, 24)
            .mapToObj(hour -> new CaloriesDailyBreakdownResponse.HourlyCalories(
                hour,
                hourlyCalories.getOrDefault(hour, 0.0)
            ))
            .toList();

        return Optional.of(new CaloriesDailyBreakdownResponse(
            daily.getDate(), daily.getTotalCaloriesKcal(),
            daily.getFirstCalorieTime(), daily.getLastCalorieTime(),
            daily.getMostActiveHour(), daily.getMostActiveHourCalories(),
            daily.getActiveHoursCount(), hourlyBreakdown
        ));
    }

    private CaloriesDailyBreakdownResponse createEmptyBreakdown(LocalDate date) {
        List<CaloriesDailyBreakdownResponse.HourlyCalories> hourlyBreakdown = IntStream.range(0, 24)
            .mapToObj(hour -> new CaloriesDailyBreakdownResponse.HourlyCalories(hour, 0.0))
            .toList();

        return new CaloriesDailyBreakdownResponse(date, 0.0, null, null, null, 0.0, 0, hourlyBreakdown);
    }

    @Override
    public CaloriesRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<CaloriesDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, CaloriesDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(CaloriesDailyProjectionJpaEntity::getDate, d -> d));

        List<CaloriesRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            CaloriesDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(new CaloriesRangeSummaryResponse.DailyStats(
                current,
                dayData != null ? dayData.getTotalCaloriesKcal() : 0.0,
                dayData != null ? dayData.getActiveHoursCount() : 0
            ));

            current = current.plusDays(1);
        }

        double totalCalories = dailyStats.stream()
            .mapToDouble(CaloriesRangeSummaryResponse.DailyStats::totalCalories)
            .sum();

        int daysWithData = (int) dailyStats.stream()
            .filter(d -> d.totalCalories() > 0)
            .count();

        int totalDays = dailyStats.size();
        double averageCalories = totalDays > 0 ? totalCalories / totalDays : 0.0;

        return new CaloriesRangeSummaryResponse(startDate, endDate, totalCalories, averageCalories, daysWithData, dailyStats);
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
