package com.healthassistant.mealimport;

import java.util.List;
import java.util.UUID;

/**
 * Interface for async meal import job processing.
 * Using an interface allows Spring to create JDK proxy (instead of CGLIB)
 * for the @Async annotation, which is required for GraalVM native image compatibility.
 */
interface MealImportJobProcessorPort {

    void processJob(UUID jobId, MealImportJobType jobType, String deviceIdStr,
                    String description, List<MealImportJob.ImageEntry> imageData);
}
