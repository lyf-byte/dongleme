package com.dongleme.backend.dto;

public record ProfileUpdateRequest(
        String nickname,
        Double weight,
        Double weeklyGoal
) {
}
