package com.dongleme.backend.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class ShanghaiTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ShanghaiTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate weekStart(LocalDate date) {
        int offset = (date.getDayOfWeek().getValue() + 6) % 7;
        return date.minusDays(offset);
    }

    public static int effectiveStreak(Integer streak, LocalDate lastCheckDate) {
        if (lastCheckDate == null) {
            return 0;
        }
        long gap = ChronoUnit.DAYS.between(lastCheckDate, today());
        return gap == 0 || gap == 1 ? (streak == null ? 0 : streak) : 0;
    }
}
