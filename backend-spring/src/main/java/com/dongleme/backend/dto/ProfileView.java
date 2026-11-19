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
        int streak,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        int weekMinutes,
        List<LocalDate> weekDays,
        Today today,
        Totals total
) {

    public record Today(int minutes, int calories, List<String> types) {
    }

    public record Totals(int count, int minutes, int calories) {
    }
}
