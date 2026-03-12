package com.healthassistant.mealimport.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealImportJobResponse(
    String jobId,
    String status,
    String jobType,
    Object result,
    String errorMessage
) {
    public static MealImportJobResponse pending(String jobId, String jobType) {
        return new MealImportJobResponse(jobId, "PENDING", jobType, null, null);
    }

    public static MealImportJobResponse processing(String jobId, String jobType) {
        return new MealImportJobResponse(jobId, "PROCESSING", jobType, null, null);
    }

    public static MealImportJobResponse done(String jobId, String jobType, Object result) {
        return new MealImportJobResponse(jobId, "DONE", jobType, result, null);
    }

    public static MealImportJobResponse failed(String jobId, String jobType, String errorMessage) {
        return new MealImportJobResponse(jobId, "FAILED", jobType, null, errorMessage);
    }
}
