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
    public SleepDailyDetailResponse getDailyDetail(LocalDate date) {
        Optional<SleepDailyDetailResponse> result = getDailyDetailInternal(date);
        return result.orElseGet(() -> createEmptyDetail(date));
    }

    private Optional<SleepDailyDetailResponse> getDailyDetailInternal(LocalDate date) {
        Optional<SleepDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDate(date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        SleepDailyProjectionJpaEntity daily = dailyOpt.get();
        List<SleepSessionProjectionJpaEntity> sessions =
            sessionRepository.findByDateOrderBySessionNumberAsc(date);

        List<SleepDailyDetailResponse.SleepSession> sessionResponses = sessions.stream()
            .map(s -> SleepDailyDetailResponse.SleepSession.builder()
                .sessionNumber(s.getSessionNumber())
                .sleepStart(s.getSleepStart())
                .sleepEnd(s.getSleepEnd())
                .durationMinutes(s.getDurationMinutes())
                .lightSleepMinutes(s.getLightSleepMinutes())
                .deepSleepMinutes(s.getDeepSleepMinutes())
                .remSleepMinutes(s.getRemSleepMinutes())
                .awakeMinutes(s.getAwakeMinutes())
                .build())
            .toList();

        return Optional.of(SleepDailyDetailResponse.builder()
            .date(daily.getDate())
            .totalSleepMinutes(daily.getTotalSleepMinutes())
            .sleepCount(daily.getSleepCount())
            .firstSleepStart(daily.getFirstSleepStart())
            .lastSleepEnd(daily.getLastSleepEnd())
            .longestSessionMinutes(daily.getLongestSessionMinutes())
            .shortestSessionMinutes(daily.getShortestSessionMinutes())
            .averageSessionMinutes(daily.getAverageSessionMinutes())
            .totalLightSleepMinutes(daily.getTotalLightSleepMinutes())
            .totalDeepSleepMinutes(daily.getTotalDeepSleepMinutes())
            .totalRemSleepMinutes(daily.getTotalRemSleepMinutes())
            .totalAwakeMinutes(daily.getTotalAwakeMinutes())
            .sessions(sessionResponses)
            .build());
    }

    private SleepDailyDetailResponse createEmptyDetail(LocalDate date) {
        return SleepDailyDetailResponse.builder()
            .date(date)
            .totalSleepMinutes(0)
            .sleepCount(0)
            .firstSleepStart(null)
            .lastSleepEnd(null)
            .longestSessionMinutes(0)
            .shortestSessionMinutes(0)
            .averageSessionMinutes(0)
            .totalLightSleepMinutes(0)
            .totalDeepSleepMinutes(0)
            .totalRemSleepMinutes(0)
            .totalAwakeMinutes(0)
            .sessions(List.of())
            .build();
    }

    @Override
    public SleepRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate) {
        List<SleepDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);

        Map<LocalDate, SleepDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(SleepDailyProjectionJpaEntity::getDate, d -> d));

        List<SleepRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            SleepDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(SleepRangeSummaryResponse.DailyStats.builder()
                .date(current)
                .totalSleepMinutes(dayData != null ? dayData.getTotalSleepMinutes() : 0)
                .sleepCount(dayData != null ? dayData.getSleepCount() : 0)
                .lightSleepMinutes(dayData != null ? dayData.getTotalLightSleepMinutes() : 0)
                .deepSleepMinutes(dayData != null ? dayData.getTotalDeepSleepMinutes() : 0)
                .remSleepMinutes(dayData != null ? dayData.getTotalRemSleepMinutes() : 0)
                .build());

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

        // Find day with most/least sleep
        SleepRangeSummaryResponse.DayExtreme dayWithMostSleep = dailyStats.stream()
            .filter(d -> d.totalSleepMinutes() > 0)
            .max((d1, d2) -> Integer.compare(d1.totalSleepMinutes(), d2.totalSleepMinutes()))
            .map(d -> SleepRangeSummaryResponse.DayExtreme.builder()
                .date(d.date())
                .sleepMinutes(d.totalSleepMinutes())
                .build())
            .orElse(null);

        SleepRangeSummaryResponse.DayExtreme dayWithLeastSleep = dailyStats.stream()
            .filter(d -> d.totalSleepMinutes() > 0)
            .min((d1, d2) -> Integer.compare(d1.totalSleepMinutes(), d2.totalSleepMinutes()))
            .map(d -> SleepRangeSummaryResponse.DayExtreme.builder()
                .date(d.date())
                .sleepMinutes(d.totalSleepMinutes())
                .build())
            .orElse(null);

        // Sleep phase totals and averages
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

        return SleepRangeSummaryResponse.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalSleepMinutes(totalSleepMinutes)
            .averageSleepMinutes(averageSleepMinutes)
            .daysWithData(daysWithData)
            .dayWithMostSleep(dayWithMostSleep)
            .dayWithLeastSleep(dayWithLeastSleep)
            .totalLightSleepMinutes(totalLightSleep)
            .totalDeepSleepMinutes(totalDeepSleep)
            .totalRemSleepMinutes(totalRemSleep)
            .averageLightSleepMinutes(averageLightSleep)
            .averageDeepSleepMinutes(averageDeepSleep)
            .averageRemSleepMinutes(averageRemSleep)
            .dailyStats(dailyStats)
            .build();
    }
}
