package com.healthassistant.sleep;

record SleepStages(
        Integer lightMinutes,
        Integer deepMinutes,
        Integer remMinutes,
        Integer awakeMinutes
) {

    static final SleepStages EMPTY = new SleepStages(null, null, null, null);

    static SleepStages of(Integer light, Integer deep, Integer rem, Integer awake) {
        return new SleepStages(light, deep, rem, awake);
    }

    boolean hasData() {
        return lightMinutes != null || deepMinutes != null || remMinutes != null || awakeMinutes != null;
    }

    int lightOrZero() {
        return lightMinutes != null ? lightMinutes : 0;
    }

    int deepOrZero() {
        return deepMinutes != null ? deepMinutes : 0;
    }

    int remOrZero() {
        return remMinutes != null ? remMinutes : 0;
    }

    int awakeOrZero() {
        return awakeMinutes != null ? awakeMinutes : 0;
    }

    SleepStages add(SleepStages other) {
        return new SleepStages(
                sumNullable(lightMinutes, other.lightMinutes),
                sumNullable(deepMinutes, other.deepMinutes),
                sumNullable(remMinutes, other.remMinutes),
                sumNullable(awakeMinutes, other.awakeMinutes)
        );
    }

    private static Integer sumNullable(Integer a, Integer b) {
        if (a == null && b == null) {
            return null;
        }
        return (a != null ? a : 0) + (b != null ? b : 0);
    }
}
