package com.healthassistant.bodymeasurements;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class BodyMeasurementsProjector {

    private final BodyMeasurementProjectionJpaRepository repository;
    private final BodyMeasurementFactory bodyMeasurementFactory;

    @Transactional
    public void projectBodyMeasurement(StoredEventData eventData) {
        Objects.requireNonNull(eventData, "eventData cannot be null");

        if (eventData.deviceId() == null || eventData.eventId() == null) {
            log.error("Invalid event data: missing deviceId or eventId, skipping projection");
            return;
        }

        bodyMeasurementFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    private void saveProjection(BodyMeasurement measurement) {
        try {
            doSaveProjection(measurement);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for body measurement {}/{}, retrying once",
                    SecurityUtils.maskDeviceId(measurement.deviceId()), measurement.date());
            doSaveProjection(measurement);
        }
    }

    private void doSaveProjection(BodyMeasurement measurement) {
        Optional<BodyMeasurementProjectionJpaEntity> existingOpt =
                repository.findByEventId(measurement.eventId());

        BodyMeasurementProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.applyMeasurementCorrection(measurement);
            log.debug("Updating existing body measurement for event {}", measurement.eventId());
        } else {
            entity = BodyMeasurementProjectionJpaEntity.from(measurement);
            log.debug("Creating new body measurement for date {} from event {}",
                    measurement.date(), measurement.eventId());
        }

        repository.save(entity);
    }

    @Transactional
    public void deleteByEventId(String eventId) {
        repository.findByEventId(eventId).ifPresent(entity -> {
            repository.deleteByEventId(eventId);
            log.info("Deleted body measurement projection for eventId: {}", eventId);
        });
    }
}
