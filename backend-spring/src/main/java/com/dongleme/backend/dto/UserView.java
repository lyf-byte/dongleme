package com.dongleme.backend.dto;

import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record UserView(
        String id,
        String email,
        String phone,
        String username,
        String account,
        String nickname,
        double weight,
        Integer weeklyGoal,
        int streak,
        @JsonProperty("isAdmin") boolean isAdmin,
        boolean onboarded,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
) {

    public static UserView of(User user) {
        String account = user.getUsername() != null
                ? user.getUsername()
                : user.getEmail() != null
                        ? user.getEmail()
                        : user.getPhone() != null ? user.getPhone() : "";

        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getUsername(),
                account,
                user.getNickname(),
                user.getWeight() == null ? 60 : user.getWeight().doubleValue(),
                user.getWeeklyGoal(),
                ShanghaiTime.effectiveStreak(user.getStreak(), user.getLastCheckDate()),
                Boolean.TRUE.equals(user.getIsAdmin()),
                Boolean.TRUE.equals(user.getOnboarded()),
                user.getCreatedAt()
        );
    }
}
