package com.dongleme.backend.security;

import com.dongleme.backend.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuthUser(
        String id,
        String email,
        String phone,
        String username,
        String nickname,
        BigDecimal weight,
        Integer weeklyGoal,
        int streak,
        boolean admin,
        boolean onboarded,
        LocalDateTime createdAt
) {

    public static AuthUser of(User user) {
        return new AuthUser(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getUsername(),
                user.getNickname(),
                user.getWeight(),
                user.getWeeklyGoal(),
                com.dongleme.backend.common.ShanghaiTime.effectiveStreak(
                        user.getStreak(), user.getLastCheckDate()),
                Boolean.TRUE.equals(user.getIsAdmin()),
                Boolean.TRUE.equals(user.getOnboarded()),
                user.getCreatedAt()
        );
    }
}
