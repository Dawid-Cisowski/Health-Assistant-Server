package com.healthassistant.workout;

import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.ExerciseStatisticsResponse;
import com.healthassistant.workout.api.dto.ExerciseStatisticsResponse.ExerciseSummary;
import com.healthassistant.workout.api.dto.ExerciseStatisticsResponse.SetEntry;
import com.healthassistant.workout.api.dto.ExerciseStatisticsResponse.WorkoutHistoryEntry;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse.PersonalRecordEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ExerciseStatisticsService {

    private final ExerciseCatalog exerciseCatalog;
    private final WorkoutExerciseProjectionJpaRepository exerciseRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;
    private final WorkoutProjectionJpaRepository workoutRepository;

    boolean exerciseExistsInCatalog(String exerciseId) {
        return exerciseCatalog.findById(exerciseId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<ExerciseStatisticsResponse> getStatistics(
            String deviceId,
            String exerciseId,
            LocalDate fromDate,
            LocalDate toDate) {

        ExerciseDefinition exercise = exerciseCatalog.findById(exerciseId)
                .orElse(null);

        if (exercise == null) {
            log.debug("Exercise not found in catalog: {}", exerciseId);
            return Optional.empty();
        }

        List<WorkoutExerciseProjectionJpaEntity> projections = queryProjectionsByExerciseId(
                deviceId, exerciseId, fromDate, toDate);

        if (projections.isEmpty()) {
            log.debug("No workout data found for exercise {} and device {}", exerciseId, deviceId);
            return Optional.empty();
        }

        List<String> workoutIds = projections.stream()
                .map(p -> p.getWorkout().getWorkoutId())
                .distinct()
                .toList();

        Map<String, List<WorkoutSetProjectionJpaEntity>> setsByWorkout = loadSetsByExerciseId(workoutIds, exerciseId);

        ExerciseSummary summary = calculateSummary(projections, setsByWorkout);
        List<WorkoutHistoryEntry> history = buildHistory(projections, setsByWorkout, summary.personalRecordKg());

        return Optional.of(new ExerciseStatisticsResponse(
                exercise.id(),
                exercise.name(),
                exercise.primaryMuscle(),
                exercise.description(),
                summary,
                history
        ));
    }

    private List<WorkoutExerciseProjectionJpaEntity> queryProjectionsByExerciseId(
            String deviceId,
            String exerciseId,
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate != null && toDate != null) {
            return exerciseRepository.findByDeviceIdAndExerciseIdAndDateRangeOrderByPerformedAtDesc(
                    deviceId, exerciseId, fromDate, toDate);
        }
        return exerciseRepository.findByDeviceIdAndExerciseIdOrderByPerformedAtDesc(
                deviceId, exerciseId);
    }

    private Map<String, List<WorkoutSetProjectionJpaEntity>> loadSetsByExerciseId(
            List<String> workoutIds,
            String exerciseId) {

        if (workoutIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return setRepository.findByWorkoutIdsAndExerciseId(workoutIds, exerciseId)
                .stream()
                .collect(Collectors.groupingBy(WorkoutSetProjectionJpaEntity::getWorkoutId));
    }

    private ExerciseSummary calculateSummary(
            List<WorkoutExerciseProjectionJpaEntity> projections,
            Map<String, List<WorkoutSetProjectionJpaEntity>> setsByWorkout) {

        Map<String, BigDecimal> maxWeightByWorkout = projections.stream()
                .map(p -> p.getWorkout().getWorkoutId())
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        workoutId -> findMaxWorkingWeight(setsByWorkout.get(workoutId))
                ));

        record AggregatedStats(int totalSets, BigDecimal totalVolume, BigDecimal prWeight, LocalDate prDate) {
            static AggregatedStats empty() {
                return new AggregatedStats(0, BigDecimal.ZERO, BigDecimal.ZERO, null);
            }

            AggregatedStats accumulate(WorkoutExerciseProjectionJpaEntity projection, BigDecimal maxWeight) {
                boolean isNewPr = maxWeight.compareTo(prWeight) > 0;
                return new AggregatedStats(
                        totalSets + projection.getTotalSets(),
                        totalVolume.add(projection.getTotalVolumeKg()),
                        isNewPr ? maxWeight : prWeight,
                        isNewPr ? projection.getWorkout().getPerformedDate() : prDate
                );
            }

            AggregatedStats combine(AggregatedStats other) {
                boolean otherHasHigherPr = other.prWeight.compareTo(prWeight) > 0;
                return new AggregatedStats(
                        totalSets + other.totalSets,
                        totalVolume.add(other.totalVolume),
                        otherHasHigherPr ? other.prWeight : prWeight,
                        otherHasHigherPr ? other.prDate : prDate
                );
            }
        }

        AggregatedStats stats = projections.stream()
                .reduce(
                        AggregatedStats.empty(),
                        (acc, p) -> acc.accumulate(p, maxWeightByWorkout.get(p.getWorkout().getWorkoutId())),
                        AggregatedStats::combine
                );

        List<ExerciseStatisticsCalculator.DataPoint> dataPoints = projections.stream()
                .map(p -> new ExerciseStatisticsCalculator.DataPoint(
                        p.getWorkout().getPerformedDate(),
                        maxWeightByWorkout.get(p.getWorkout().getWorkoutId())
                ))
                .filter(dp -> dp.value().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal progressionPercentage = ExerciseStatisticsCalculator.calculateProgressionPercentage(dataPoints);

        return new ExerciseSummary(stats.prWeight(), stats.prDate(), stats.totalSets(), stats.totalVolume(), progressionPercentage);
    }

    private BigDecimal findMaxWorkingWeight(List<WorkoutSetProjectionJpaEntity> sets) {
        return isNullOrEmpty(sets)
                ? BigDecimal.ZERO
                : sets.stream()
                        .filter(s -> !Boolean.TRUE.equals(s.getIsWarmup()))
                        .map(WorkoutSetProjectionJpaEntity::getWeightKg)
                        .max(Comparator.naturalOrder())
                        .orElse(BigDecimal.ZERO);
    }

    private List<WorkoutHistoryEntry> buildHistory(
            List<WorkoutExerciseProjectionJpaEntity> projections,
            Map<String, List<WorkoutSetProjectionJpaEntity>> setsByWorkout,
            BigDecimal prWeight) {

        return projections.stream()
                .map(p -> buildWorkoutEntry(p, setsByWorkout.get(p.getWorkout().getWorkoutId()), prWeight))
                .toList();
    }

    private WorkoutHistoryEntry buildWorkoutEntry(
            WorkoutExerciseProjectionJpaEntity projection,
            List<WorkoutSetProjectionJpaEntity> sets,
            BigDecimal prWeight) {

        List<SetEntry> setEntries = buildSetEntries(sets, prWeight);

        BigDecimal maxWeightKg = isNullOrEmpty(sets) ? BigDecimal.ZERO : findMaxWorkingWeight(sets);
        BigDecimal estimated1RM = calculateBest1RM(sets);

        return new WorkoutHistoryEntry(
                projection.getWorkout().getWorkoutId(),
                projection.getWorkout().getPerformedDate(),
                maxWeightKg,
                estimated1RM,
                projection.getTotalSets(),
                setEntries
        );
    }

    private List<SetEntry> buildSetEntries(List<WorkoutSetProjectionJpaEntity> sets, BigDecimal prWeight) {
        return isNullOrEmpty(sets)
                ? Collections.emptyList()
                : sets.stream()
                        .sorted(Comparator.comparing(WorkoutSetProjectionJpaEntity::getSetNumber))
                        .map(s -> new SetEntry(
                                s.getSetNumber(),
                                s.getWeightKg(),
                                s.getReps(),
                                !Boolean.TRUE.equals(s.getIsWarmup()) && s.getWeightKg().compareTo(prWeight) == 0
                        ))
                        .toList();
    }

    private BigDecimal calculateBest1RM(List<WorkoutSetProjectionJpaEntity> sets) {
        return isNullOrEmpty(sets)
                ? BigDecimal.ZERO
                : sets.stream()
                        .filter(s -> !Boolean.TRUE.equals(s.getIsWarmup()))
                        .map(s -> ExerciseStatisticsCalculator.calculateEstimated1RM(s.getWeightKg(), s.getReps()))
                        .max(Comparator.naturalOrder())
                        .orElse(BigDecimal.ZERO);
    }

    private static <T> boolean isNullOrEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    @Transactional(readOnly = true)
    public PersonalRecordsResponse getAllPersonalRecords(String deviceId) {
        List<WorkoutSetProjectionJpaRepository.PersonalRecordProjection> allSets =
                setRepository.findAllSetsForPersonalRecords(deviceId);

        if (allSets.isEmpty()) {
            return new PersonalRecordsResponse(List.of());
        }

        Map<String, List<WorkoutSetProjectionJpaRepository.PersonalRecordProjection>> setsByExercise =
                allSets.stream()
                        .collect(Collectors.groupingBy(this::getExerciseKey));

        List<PersonalRecordEntry> records = setsByExercise.values().stream()
                .map(this::buildPersonalRecordEntry)
                .sorted(Comparator.comparing(PersonalRecordEntry::exerciseName))
                .toList();

        return new PersonalRecordsResponse(records);
    }

    private String getExerciseKey(WorkoutSetProjectionJpaRepository.PersonalRecordProjection projection) {
        return projection.getExerciseId() != null
                ? projection.getExerciseId()
                : projection.getExerciseName();
    }

    private PersonalRecordEntry buildPersonalRecordEntry(
            List<WorkoutSetProjectionJpaRepository.PersonalRecordProjection> sets) {

        WorkoutSetProjectionJpaRepository.PersonalRecordProjection prSet = sets.stream()
                .max(Comparator.comparing(WorkoutSetProjectionJpaRepository.PersonalRecordProjection::getWeightKg)
                        .thenComparing(WorkoutSetProjectionJpaRepository.PersonalRecordProjection::getPerformedDate,
                                Comparator.reverseOrder()))
                .orElseThrow();

        String exerciseId = prSet.getExerciseId();
        String exerciseName = prSet.getExerciseName();
        String muscleGroup = exerciseId != null
                ? exerciseCatalog.findById(exerciseId)
                        .map(ExerciseDefinition::primaryMuscle)
                        .orElse(null)
                : null;

        return new PersonalRecordEntry(
                exerciseId,
                exerciseName,
                muscleGroup,
                prSet.getWeightKg(),
                prSet.getPerformedDate()
        );
    }
}
