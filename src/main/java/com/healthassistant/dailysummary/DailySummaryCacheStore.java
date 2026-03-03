package com.healthassistant.dailysummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Handles short-lived transactional reads and writes for AI-generated summaries/reports.
 * Exists as a separate bean so that AI generation (slow) can happen outside any transaction,
 * preventing ObjectOptimisticLockingFailureException caused by holding a tx open for seconds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryCacheStore {

    private final DailySummaryJpaRepository repository;

    @Transactional(readOnly = true)
    Optional<DailySummaryJpaEntity> findByDeviceAndDate(String deviceId, LocalDate date) {
        return repository.findByDeviceIdAndDate(deviceId, date);
    }

    @Transactional
    void trySaveSummaryCache(String deviceId, LocalDate date, String aiSummary) {
        try {
            repository.findByDeviceIdAndDate(deviceId, date).ifPresent(entity -> {
                entity.cacheAiSummary(aiSummary);
                repository.save(entity);
                log.info("Cached AI summary for date: {}", date);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict saving AI summary for device {} date {}: concurrent update detected, result not cached",
                    maskDeviceId(deviceId), date);
        }
    }

    @Transactional
    void trySaveReportCache(String deviceId, LocalDate date, String aiReport) {
        try {
            repository.findByDeviceIdAndDate(deviceId, date).ifPresent(entity -> {
                entity.cacheAiReport(aiReport);
                repository.save(entity);
                log.info("Cached AI report for date: {}", date);
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict saving AI report for device {} date {}: concurrent update detected, result not cached",
                    maskDeviceId(deviceId), date);
        }
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
