package com.healthassistant.sleep;

import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.sleep.api.dto.SleepDailyDetailResponse;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;
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
public class SleepService implements SleepFacade {

    private final SleepDailyProjectionJpaRepository dailyRepository;
    private final SleepSessionProjectionJpaRepository sessionRepository;

    @Override
    public SleepDailyDetailResponse getDailyDetail(String deviceId, LocalDate date) {
        Optional<SleepDailyDetailResponse> result = getDailyDetailInternal(deviceId, date);
        return result.orElseGet(() -> createEmptyDetail(date));
    }

    private Optional<SleepDailyDetailResponse> getDailyDetailInternal(String deviceId, LocalDate date) {
        Optional<SleepDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        SleepDailyProjectionJpaEntity daily = dailyOpt.get();
        List<SleepSessionProjectionJpaEntity> sessions =
            sessionRepository.findByDeviceIdAndDateOrderBySessionNumberAsc(deviceId, date);

        List<SleepDailyDetailResponse.SleepSession> sessionResponses = sessions.stream()
            .map(s -> new SleepDailyDetailResponse.SleepSession(
                s.getSessionNumber(), s.getSleepStart(), s.getSleepEnd(), s.getDurationMinutes(),
                s.getLightSleepMinutes(), s.getDeepSleepMinutes(), s.getRemSleepMinutes(),
                s.getAwakeMinutes(), s.getSleepScore()))
            .toList();

        return Optional.of(new SleepDailyDetailResponse(
            daily.getDate(), daily.getTotalSleepMinutes(), daily.getSleepCount(),
            daily.getFirstSleepStart(), daily.getLastSleepEnd(),
            daily.getLongestSessionMinutes(), daily.getShortestSessionMinutes(), daily.getAverageSessionMinutes(),
            daily.getTotalLightSleepMinutes(), daily.getTotalDeepSleepMinutes(),
            daily.getTotalRemSleepMinutes(), daily.getTotalAwakeMinutes(), sessionResponses
        ));
    }

    private SleepDailyDetailResponse createEmptyDetail(LocalDate date) {
        return new SleepDailyDetailResponse(date, 0, 0, null, null, 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    @Override
    public SleepRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<SleepDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, SleepDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(SleepDailyProjectionJpaEntity::getDate, d -> d));

        List<SleepRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            SleepDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(new SleepRangeSummaryResponse.DailyStats(
                current,
                dayData != null ? dayData.getTotalSleepMinutes() : 0,
                dayData != null ? dayData.getSleepCount() : 0,
                dayData != null ? dayData.getTotalLightSleepMinutes() : 0,
                dayData != null ? dayData.getTotalDeepSleepMinutes() : 0,
                dayData != null ? dayData.getTotalRemSleepMinutes() : 0
            ));

            current = current.plusDays(1);
        }

        int totalSleepMinutes = dailyStats.stream()
            .mapToInt(SleepRangeSummaryResponse.DailyStats::totalSleepMinutes)
            .sum();

        int daysWithData = (int) dailyStats.stream()
            .filter(d -> d.totalSleepMinutes() > 0)
            .count();

        int totalDays = dailyStats.size();
        int averageSleepMinutes = totalDays > 0 ? totalSleepMinutes / totalDays : 0;

        SleepRangeSummaryResponse.DayExtreme dayWithMostSleep = dailyStats.stream()
            .filter(d -> d.totalSleepMinutes() > 0)
            .max((d1, d2) -> Integer.compare(d1.totalSleepMinutes(), d2.totalSleepMinutes()))
            .map(d -> new SleepRangeSummaryResponse.DayExtreme(d.date(), d.totalSleepMinutes()))
            .orElse(null);

        SleepRangeSummaryResponse.DayExtreme dayWithLeastSleep = dailyStats.stream()
            .filter(d -> d.totalSleepMinutes() > 0)
            .min((d1, d2) -> Integer.compare(d1.totalSleepMinutes(), d2.totalSleepMinutes()))
            .map(d -> new SleepRangeSummaryResponse.DayExtreme(d.date(), d.totalSleepMinutes()))
            .orElse(null);

        int totalLightSleep = dailyStats.stream()
            .mapToInt(d -> d.lightSleepMinutes() != null ? d.lightSleepMinutes() : 0)
            .sum();

        int totalDeepSleep = dailyStats.stream()
            .mapToInt(d -> d.deepSleepMinutes() != null ? d.deepSleepMinutes() : 0)
            .sum();

        int totalRemSleep = dailyStats.stream()
            .mapToInt(d -> d.remSleepMinutes() != null ? d.remSleepMinutes() : 0)
            .sum();

        int averageLightSleep = totalDays > 0 ? totalLightSleep / totalDays : 0;
        int averageDeepSleep = totalDays > 0 ? totalDeepSleep / totalDays : 0;
        int averageRemSleep = totalDays > 0 ? totalRemSleep / totalDays : 0;

        return new SleepRangeSummaryResponse(
            startDate, endDate, totalSleepMinutes, averageSleepMinutes, daysWithData,
            dayWithMostSleep, dayWithLeastSleep, totalLightSleep, totalDeepSleep, totalRemSleep,
            averageLightSleep, averageDeepSleep, averageRemSleep, dailyStats
        );
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all sleep projections");
        sessionRepository.deleteAll();
        dailyRepository.deleteAll();
    }
}
