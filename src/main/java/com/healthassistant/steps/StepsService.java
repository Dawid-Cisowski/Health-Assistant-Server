package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;
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
class StepsService implements StepsFacade {

    private final StepsDailyProjectionJpaRepository dailyRepository;
    private final StepsHourlyProjectionJpaRepository hourlyRepository;
    private final StepsProjector stepsProjector;

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) return "***";
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @Override
    public StepsDailyBreakdownResponse getDailyBreakdown(String deviceId, LocalDate date) {
        Optional<StepsDailyBreakdownResponse> result = getDailyBreakdownInternal(deviceId, date);
        return result.orElseGet(() -> createEmptyBreakdown(date));
    }

    private Optional<StepsDailyBreakdownResponse> getDailyBreakdownInternal(String deviceId, LocalDate date) {
        Optional<StepsDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        StepsDailyProjectionJpaEntity daily = dailyOpt.get();
        List<StepsHourlyProjectionJpaEntity> hourlyData =
            hourlyRepository.findByDeviceIdAndDateOrderByHourAsc(deviceId, date);

        Map<Integer, Integer> hourlySteps = hourlyData.stream()
            .collect(Collectors.toMap(
                StepsHourlyProjectionJpaEntity::getHour,
                StepsHourlyProjectionJpaEntity::getStepCount
            ));

        List<StepsDailyBreakdownResponse.HourlySteps> hourlyBreakdown = IntStream.range(0, 24).mapToObj(hour -> new StepsDailyBreakdownResponse.HourlySteps(
                hour,
                hourlySteps.getOrDefault(hour, 0)
        )).collect(Collectors.toList());

        return Optional.of(new StepsDailyBreakdownResponse(
            daily.getDate(),
            daily.getTotalSteps(),
            daily.getFirstStepTime(),
            daily.getLastStepTime(),
            daily.getMostActiveHour(),
            daily.getMostActiveHourSteps(),
            daily.getActiveHoursCount(),
            hourlyBreakdown
        ));
    }

    private StepsDailyBreakdownResponse createEmptyBreakdown(LocalDate date) {
        List<StepsDailyBreakdownResponse.HourlySteps> hourlyBreakdown = IntStream.range(0, 24)
            .mapToObj(hour -> new StepsDailyBreakdownResponse.HourlySteps(hour, 0))
            .collect(Collectors.toList());

        return new StepsDailyBreakdownResponse(date, 0, null, null, null, 0, 0, hourlyBreakdown);
    }
    @Override
    public StepsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<StepsDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, StepsDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(StepsDailyProjectionJpaEntity::getDate, d -> d));

        List<StepsRangeSummaryResponse.DailyStats> dailyStats = startDate
            .datesUntil(endDate.plusDays(1))
            .map(date -> {
                StepsDailyProjectionJpaEntity dayData = dataByDate.get(date);
                return new StepsRangeSummaryResponse.DailyStats(
                    date,
                    dayData != null ? dayData.getTotalSteps() : 0,
                    dayData != null ? dayData.getActiveHoursCount() : 0
                );
            })
            .toList();

        int totalSteps = dailyStats.stream()
            .mapToInt(StepsRangeSummaryResponse.DailyStats::totalSteps)
            .sum();

        int daysWithData = (int) dailyStats.stream()
            .filter(d -> d.totalSteps() > 0)
            .count();

        int totalDays = dailyStats.size();
        int averageSteps = totalDays > 0 ? totalSteps / totalDays : 0;

        return new StepsRangeSummaryResponse(startDate, endDate, totalSteps, averageSteps, daysWithData, dailyStats);
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all steps projections");
        hourlyRepository.deleteAll();
        dailyRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteProjectionsByDeviceId(String deviceId) {
        log.debug("Deleting steps projections for device: {}", maskDeviceId(deviceId));
        hourlyRepository.deleteByDeviceId(deviceId);
        dailyRepository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting steps projections for device {} date {}", maskDeviceId(deviceId), date);
        hourlyRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} steps events directly", events.size());
        events.forEach(event -> {
            try {
                stepsProjector.projectSteps(event);
            } catch (Exception e) {
                log.error("Failed to project steps event: {}", event.eventId().value(), e);
            }
        });
    }
}
