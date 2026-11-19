package com.dongleme.backend.dto;

import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ProfileBasicView(
        String nickname,
        String gender,
        double weight,
        Integer weeklyGoal,
        int streak,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
) {

    public static ProfileBasicView of(User user) {
        return new ProfileBasicView(
                user.getNickname(),
                user.getGender() == null ? "secret" : user.getGender(),
                user.getWeight() == null ? 60 : user.getWeight().doubleValue(),
                user.getWeeklyGoal(),
                ShanghaiTime.effectiveStreak(user.getStreak(), user.getLastCheckDate()),
                user.getCreatedAt()
        );
    }
}
