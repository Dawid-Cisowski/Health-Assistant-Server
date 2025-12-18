package com.healthassistant.workout;

record ExerciseSet(
        int setNumber,
        Weight weight,
        Reps reps,
        boolean warmup,
        Volume volume
) {

    ExerciseSet(int setNumber, Weight weight, Reps reps, boolean warmup) {
        this(setNumber, weight, reps, warmup, Volume.calculate(weight, reps));
    }

    static ExerciseSet of(int setNumber, double weightKg, int reps, boolean warmup) {
        Weight weight = Weight.of(weightKg);
        Reps repsVo = Reps.of(reps);
        return new ExerciseSet(setNumber, weight, repsVo, warmup);
    }

    boolean isWorkingSet() {
        return !warmup;
    }
}
