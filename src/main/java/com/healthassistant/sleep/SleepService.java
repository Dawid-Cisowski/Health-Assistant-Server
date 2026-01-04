package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoredEventData;
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
class SleepService implements SleepFacade {

    private static final String SLEEP_SESSION_V1 = "SleepSessionRecorded.v1";

    private final SleepDailyProjectionJpaRepository dailyRepository;
    private final SleepSessionProjectionJpaRepository sessionRepository;
    private final HealthEventsFacade healthEventsFacade;
    private final SleepProjector sleepProjector;

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

    @Override
    @Transactional
    public void deleteProjectionsByDeviceId(String deviceId) {
        log.debug("Deleting sleep projections for device: {}", deviceId);
        sessionRepository.deleteByDeviceId(deviceId);
        dailyRepository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting sleep projections for device {} date {}", deviceId, date);
        sessionRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
    }

    @Override
    @Transactional
    public int rebuildProjections(String deviceId) {
        log.info("Rebuilding sleep projections for device: {}", deviceId);

        int rebuiltCount = 0;
        int page = 0;
        int pageSize = 100;
        List<StoredEventData> events;

        do {
            events = healthEventsFacade.findEventsForReprojection(page, pageSize);

            List<StoredEventData> sleepEvents = events.stream()
                    .filter(e -> SLEEP_SESSION_V1.equals(e.eventType().value()))
                    .filter(e -> deviceId.equals(e.deviceId().value()))
                    .toList();

            for (StoredEventData event : sleepEvents) {
                try {
                    sleepProjector.projectSleep(event);
                    rebuiltCount++;
                    log.debug("Rebuilt projection for sleep event: {}", event.eventId().value());
                } catch (Exception e) {
                    log.error("Failed to rebuild projection for sleep event: {}", event.eventId().value(), e);
                }
            }

            page++;
        } while (events.size() == pageSize);

        log.info("Rebuilt {} sleep projections for device: {}", rebuiltCount, deviceId);
        return rebuiltCount;
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} sleep events directly", events.size());
        for (StoredEventData event : events) {
            try {
                sleepProjector.projectSleep(event);
            } catch (Exception e) {
                log.error("Failed to project sleep event: {}", event.eventId().value(), e);
            }
        }
    }
}
