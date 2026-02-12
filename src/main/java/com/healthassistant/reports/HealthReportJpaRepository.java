package com.healthassistant.reports;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

interface HealthReportJpaRepository extends JpaRepository<HealthReportJpaEntity, Long> {

    Page<HealthReportJpaEntity> findByDeviceIdAndReportTypeOrderByGeneratedAtDesc(
            String deviceId, String reportType, Pageable pageable);

    Optional<HealthReportJpaEntity> findByDeviceIdAndReportTypeAndPeriodStartAndPeriodEnd(
            String deviceId, String reportType, LocalDate periodStart, LocalDate periodEnd);
}
