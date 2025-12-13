package com.healthassistant.config;

import com.healthassistant.assistant.api.AssistantFacade;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.googlefit.api.GoogleFitFacade;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.workout.api.WorkoutFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final StepsFacade stepsFacade;
    private final WorkoutFacade workoutFacade;
    private final SleepFacade sleepFacade;
    private final DailySummaryFacade dailySummaryFacade;
    private final HealthEventsFacade healthEventsFacade;
    private final GoogleFitFacade googleFitFacade;
    private final AssistantFacade assistantFacade;

    @Transactional
    public void deleteAllData() {
        log.warn("Deleting all data from database - this operation is irreversible");

        // Delete projection data first (child tables)
        log.info("Deleting workout projection data");
        workoutFacade.deleteAllProjections();

        log.info("Deleting steps projection data");
        stepsFacade.deleteAllProjections();

        log.info("Deleting sleep projection data");
        sleepFacade.deleteAllProjections();

        // Delete aggregated data
        log.info("Deleting daily summaries");
        dailySummaryFacade.deleteAllSummaries();

        // Delete source events
        log.info("Deleting all health events");
        healthEventsFacade.deleteAllEvents();

        // Delete sync state
        log.info("Deleting Google Fit sync state");
        googleFitFacade.deleteAllSyncState();

        // Delete AI conversation data
        log.info("Deleting conversations");
        assistantFacade.deleteAllConversations();

        log.warn("All data has been deleted successfully");
    }
}
