package com.healthassistant.bodymeasurements.api;

import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementLatestResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementRangeSummaryResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementSummaryResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyPart;
import com.healthassistant.bodymeasurements.api.dto.BodyPartHistoryResponse;
import com.healthassistant.bodymeasurements.api.dto.UpdateBodyMeasurementRequest;
import com.healthassistant.healthevents.api.dto.StoredEventData;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Public facade for the Body Measurements module.
 * Provides operations for tracking and querying body dimension measurements
 * (biceps, waist, chest, thighs, etc.).
 */
public interface BodyMeasurementsFacade {

    /**
     * Retrieves the most recent body measurement for a device with trend data.
     *
     * @param deviceId the device identifier
     * @return the latest measurement with trends, or empty if no measurements exist
     */
    Optional<BodyMeasurementLatestResponse> getLatestMeasurement(String deviceId);

    /**
     * Retrieves all body measurements within a date range.
     *
     * @param deviceId the device identifier
     * @param startDate the start of the date range (inclusive)
     * @param endDate the end of the date range (inclusive)
     * @return summary containing all measurements in the range
     */
    BodyMeasurementRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate);

    /**
     * Retrieves a specific body measurement by its ID.
     *
     * @param deviceId the device identifier
     * @param measurementId the measurement identifier
     * @return the measurement, or empty if not found
     */
    Optional<BodyMeasurementResponse> getMeasurementById(String deviceId, String measurementId);

    /**
     * Retrieves a dashboard summary showing the latest value for each body part
     * with change compared to the previous measurement.
     *
     * @param deviceId the device identifier
     * @return summary with latest values and changes per body part
     */
    BodyMeasurementSummaryResponse getSummary(String deviceId);

    /**
     * Retrieves measurement history for a specific body part for charting/trend analysis.
     *
     * @param deviceId the device identifier
     * @param bodyPart the body part to get history for
     * @param from the start of the date range (inclusive)
     * @param to the end of the date range (inclusive)
     * @return time-series data with statistics (min, max, change, change percent)
     */
    BodyPartHistoryResponse getBodyPartHistory(String deviceId, BodyPart bodyPart, LocalDate from, LocalDate to);

    /**
     * Deletes all body measurement projections for a specific date.
     * Used for reprojection scenarios.
     *
     * @param deviceId the device identifier
     * @param date the date to delete projections for
     */
    void deleteProjectionsForDate(String deviceId, LocalDate date);

    /**
     * Projects stored events into body measurement projections.
     *
     * @param events the events to project
     */
    void projectEvents(List<StoredEventData> events);

    /**
     * Deletes a body measurement by its event ID.
     *
     * @param deviceId the device identifier
     * @param eventId the event identifier of the body measurement to delete
     * @throws IllegalArgumentException if the event is not found or not a body measurement
     */
    void deleteBodyMeasurement(String deviceId, String eventId);

    /**
     * Updates a body measurement by correcting the original event.
     *
     * @param deviceId the device identifier
     * @param eventId the event identifier of the body measurement to update
     * @param request the update request with new measurement values
     * @return the updated body measurement response
     * @throws IllegalArgumentException if the event is not found or not a body measurement
     */
    BodyMeasurementResponse updateBodyMeasurement(String deviceId, String eventId, UpdateBodyMeasurementRequest request);
}
