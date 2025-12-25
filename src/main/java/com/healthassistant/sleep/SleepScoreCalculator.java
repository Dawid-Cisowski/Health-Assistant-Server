package com.healthassistant.sleep;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
class SleepScoreCalculator {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    int calculateScore(Instant sleepStart, Instant sleepEnd, int totalMinutes) {
        int durationScore = calculateDurationScore(totalMinutes);
        int bedtimeScore = calculateBedtimeScore(sleepStart);
        return durationScore + bedtimeScore;
    }

    private int calculateDurationScore(int totalMinutes) {
        int hours = totalMinutes / 60;

        if (hours >= 7 && hours < 9) {
            return 50;
        } else if ((hours == 6) || (hours == 9)) {
            return 40;
        } else if ((hours == 5) || (hours == 10)) {
            return 25;
        } else {
            return 10;
        }
    }

    private int calculateBedtimeScore(Instant sleepStart) {
        LocalTime bedtime = sleepStart.atZone(POLAND_ZONE).toLocalTime();
        int hour = bedtime.getHour();

        if (hour >= 21 && hour < 23) {
            return 50;
        } else if (hour == 23) {
            return 40;
        } else if (hour == 0) {
            return 30;
        } else if (hour == 1) {
            return 20;
        } else {
            return 15;
        }
    }
}
