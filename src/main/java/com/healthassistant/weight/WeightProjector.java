package com.healthassistant.weight;

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
class WeightProjector {

    private final WeightMeasurementProjectionJpaRepository repository;
    private final WeightMeasurementFactory weightMeasurementFactory;

    @Transactional
    public void projectWeight(StoredEventData eventData) {
        Objects.requireNonNull(eventData, "eventData cannot be null");

        if (eventData.deviceId() == null || eventData.eventId() == null) {
            log.error("Invalid event data: missing deviceId or eventId, skipping projection");
            return;
        }

        weightMeasurementFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    private void saveProjection(WeightMeasurement measurement) {
        try {
            doSaveProjection(measurement);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for weight measurement {}/{}, retrying once",
                    WeightSecurityUtils.maskDeviceId(measurement.deviceId()), measurement.date());
            doSaveProjection(measurement);
        }
    }

    private void doSaveProjection(WeightMeasurement measurement) {
        Optional<WeightMeasurementProjectionJpaEntity> existingOpt =
                repository.findByEventId(measurement.eventId());

        WeightMeasurementProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.applyMeasurementCorrection(measurement);
            log.debug("Updating existing weight measurement for event {}", measurement.eventId());
        } else {
            entity = WeightMeasurementProjectionJpaEntity.from(measurement);
            log.debug("Creating new weight measurement for date {} from event {}",
                    measurement.date(), measurement.eventId());
        }

        repository.save(entity);
    }

    @Transactional
    public void deleteByEventId(String eventId) {
        repository.findByEventId(eventId).ifPresent(entity -> {
            repository.deleteByEventId(eventId);
            log.info("Deleted weight projection for eventId: {}", eventId);
        });
    }
}
