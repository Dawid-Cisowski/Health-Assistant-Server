package com.healthassistant.workout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

class ExerciseStatisticsCalculator {

    private static final BigDecimal BRZYCKI_NUMERATOR = new BigDecimal("36");
    private static final int BRZYCKI_DENOMINATOR_BASE = 37;
    private static final int SCALE = 2;
    private static final BigDecimal HIGH_REP_1RM_MULTIPLIER = new BigDecimal("2.5");

    private ExerciseStatisticsCalculator() {
    }

    static BigDecimal calculateEstimated1RM(BigDecimal weightKg, int reps) {
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (reps <= 0) {
            return BigDecimal.ZERO;
        }
        if (reps == 1) {
            return weightKg.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (reps >= BRZYCKI_DENOMINATOR_BASE) {
            return weightKg.multiply(HIGH_REP_1RM_MULTIPLIER).setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal denominator = new BigDecimal(BRZYCKI_DENOMINATOR_BASE - reps);
        return weightKg.multiply(BRZYCKI_NUMERATOR)
                .divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal calculateProgressionPercentage(List<DataPoint> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return BigDecimal.ZERO;
        }

        LinearRegressionResult regression = performLinearRegression(dataPoints);

        if (regression.firstPredicted().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal diff = regression.lastPredicted().subtract(regression.firstPredicted());
        return diff.multiply(new BigDecimal("100"))
                .divide(regression.firstPredicted(), SCALE, RoundingMode.HALF_UP);
    }

    private static LinearRegressionResult performLinearRegression(List<DataPoint> dataPoints) {
        int n = dataPoints.size();
        if (n < 2) {
            BigDecimal avg = dataPoints.isEmpty() ? BigDecimal.ZERO : dataPoints.getFirst().value();
            return new LinearRegressionResult(BigDecimal.ZERO, avg, avg, avg);
        }

        LocalDate firstDate = dataPoints.stream()
                .map(DataPoint::date)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        record RegressionSums(double sumX, double sumY, double sumXY, double sumX2) {
            static RegressionSums empty() {
                return new RegressionSums(0, 0, 0, 0);
            }

            RegressionSums add(double x, double y) {
                return new RegressionSums(sumX + x, sumY + y, sumXY + x * y, sumX2 + x * x);
            }

            RegressionSums combine(RegressionSums other) {
                return new RegressionSums(
                        sumX + other.sumX,
                        sumY + other.sumY,
                        sumXY + other.sumXY,
                        sumX2 + other.sumX2
                );
            }
        }

        RegressionSums sums = IntStream.range(0, n)
                .mapToObj(i -> {
                    DataPoint dp = dataPoints.get(i);
                    double x = ChronoUnit.DAYS.between(firstDate, dp.date());
                    double y = dp.value().doubleValue();
                    return RegressionSums.empty().add(x, y);
                })
                .reduce(RegressionSums.empty(), RegressionSums::combine);

        double meanX = sums.sumX() / n;
        double meanY = sums.sumY() / n;

        double denominator = sums.sumX2() - n * meanX * meanX;
        double slope = Math.abs(denominator) < 1e-10
                ? 0
                : (sums.sumXY() - n * meanX * meanY) / denominator;
        double intercept = Math.abs(denominator) < 1e-10
                ? meanY
                : meanY - slope * meanX;

        double maxX = dataPoints.stream()
                .mapToLong(dp -> ChronoUnit.DAYS.between(firstDate, dp.date()))
                .max()
                .orElse(0);

        double firstPredicted = intercept;
        double lastPredicted = slope * maxX + intercept;

        return new LinearRegressionResult(
                BigDecimal.valueOf(slope).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(intercept).setScale(SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(firstPredicted).setScale(SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lastPredicted).setScale(SCALE, RoundingMode.HALF_UP)
        );
    }

    record DataPoint(LocalDate date, BigDecimal value) {
    }

    private record LinearRegressionResult(
            BigDecimal slope,
            BigDecimal intercept,
            BigDecimal firstPredicted,
            BigDecimal lastPredicted
    ) {
    }
}
