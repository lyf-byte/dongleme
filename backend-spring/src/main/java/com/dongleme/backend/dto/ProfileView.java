package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileView(
        String nickname,
        String gender,
        double weight,
        Integer weeklyGoal,
        Integer nextWeeklyGoal,
        int streak,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        int weekMinutes,
        List<LocalDate> weekDays,
        Today today,
        Totals total,
        List<RecentRecord> recent
) {

    public record Today(
            int minutes,
            int calories,
            int pendingMinutes,
            int pendingCalories,
            List<String> types) {
    }

    public record Totals(int count, int minutes, int calories) {
    }

    public record RecentRecord(
            LocalDate date, String type, int duration, int calories, int flagged) {
    }
}
