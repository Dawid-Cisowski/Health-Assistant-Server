package com.healthassistant.config;

import com.healthassistant.assistant.ConversationMessageRepository;
import com.healthassistant.assistant.ConversationRepository;
import com.healthassistant.dailysummary.DailySummaryJpaRepository;
import com.healthassistant.googlefit.GoogleFitSyncStateRepository;
import com.healthassistant.healthevents.HealthEventJpaRepository;
import com.healthassistant.sleep.SleepDailyProjectionJpaRepository;
import com.healthassistant.sleep.SleepSessionProjectionJpaRepository;
import com.healthassistant.steps.StepsDailyProjectionJpaRepository;
import com.healthassistant.steps.StepsHourlyProjectionJpaRepository;
import com.healthassistant.workout.WorkoutExerciseProjectionJpaRepository;
import com.healthassistant.workout.WorkoutProjectionJpaRepository;
import com.healthassistant.workout.WorkoutSetProjectionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final WorkoutSetProjectionJpaRepository workoutSetProjectionRepository;
    private final WorkoutExerciseProjectionJpaRepository workoutExerciseProjectionRepository;
    private final WorkoutProjectionJpaRepository workoutProjectionRepository;
    private final StepsHourlyProjectionJpaRepository stepsHourlyProjectionRepository;
    private final StepsDailyProjectionJpaRepository stepsDailyProjectionRepository;
    private final SleepSessionProjectionJpaRepository sleepSessionProjectionRepository;
    private final SleepDailyProjectionJpaRepository sleepDailyProjectionRepository;
    private final DailySummaryJpaRepository dailySummaryRepository;
    private final HealthEventJpaRepository healthEventRepository;
    private final GoogleFitSyncStateRepository googleFitSyncStateRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationRepository conversationRepository;

    @Transactional
    public void deleteAllData() {
        log.warn("Deleting all data from database - this operation is irreversible");

        // Delete projection data first (child tables)
        log.info("Deleting workout projection data");
        workoutSetProjectionRepository.deleteAll();
        workoutExerciseProjectionRepository.deleteAll();
        workoutProjectionRepository.deleteAll();

        log.info("Deleting steps projection data");
        stepsHourlyProjectionRepository.deleteAll();
        stepsDailyProjectionRepository.deleteAll();

        log.info("Deleting sleep projection data");
        sleepSessionProjectionRepository.deleteAll();
        sleepDailyProjectionRepository.deleteAll();

        // Delete aggregated data
        log.info("Deleting daily summaries");
        dailySummaryRepository.deleteAll();

        // Delete source events
        log.info("Deleting all health events");
        healthEventRepository.deleteAll();

        // Delete sync state
        log.info("Deleting Google Fit sync state");
        googleFitSyncStateRepository.deleteAll();

        // Delete AI conversation data
        log.info("Deleting conversation messages");
        conversationMessageRepository.deleteAll();

        log.info("Deleting conversations");
        conversationRepository.deleteAll();

        log.warn("All data has been deleted successfully");
    }
}
