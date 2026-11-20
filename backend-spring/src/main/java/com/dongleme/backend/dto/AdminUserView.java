package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminUserView(
        String id,
        String nickname,
        String gender,
        double weight,
        Integer weeklyGoal,
        int streak,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate lastCheckDate,
        @JsonProperty("isAdmin") boolean isAdmin,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        String account,
        int recordCount,
        long weekMinutes,
        long routeCount
) {
}
