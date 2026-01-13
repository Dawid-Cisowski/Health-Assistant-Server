package com.healthassistant.calories;

import com.healthassistant.calories.api.CaloriesFacade;
import com.healthassistant.calories.api.dto.CaloriesDailyBreakdownResponse;
import com.healthassistant.calories.api.dto.CaloriesRangeSummaryResponse;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final CaloriesProjector caloriesProjector;

    @Override
    public CaloriesDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date) {
        return getDailyBreakdownInternal(deviceId, date)
                .orElseGet(() -> createEmptyBreakdown(date));
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

        return new CaloriesDailyBreakdownResponse(date, 0.0, null, null, null, null, 0, hourlyBreakdown);
    }

    @Override
    public CaloriesRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<CaloriesDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, CaloriesDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(CaloriesDailyProjectionJpaEntity::getDate, d -> d));

        List<CaloriesRangeSummaryResponse.DailyStats> dailyStats = startDate.datesUntil(endDate.plusDays(1))
            .map(date -> {
                CaloriesDailyProjectionJpaEntity dayData = dataByDate.get(date);
                return new CaloriesRangeSummaryResponse.DailyStats(
                    date,
                    dayData != null ? dayData.getTotalCaloriesKcal() : 0.0,
                    dayData != null ? dayData.getActiveHoursCount() : 0
                );
            })
            .toList();

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
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting calories projections for device {} date {}", deviceId, date);
        hourlyRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} calories events directly", events.size());
        events.forEach(event -> {
            try {
                caloriesProjector.projectCalories(event);
            } catch (Exception e) {
                log.error("Failed to project calories event: {}", event.eventId().value(), e);
            }
        });
    }
}
