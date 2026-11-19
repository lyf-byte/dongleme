package com.dongleme.backend.dto;

public record ProfileUpdateRequest(
        String nickname,
        String gender,
        Double weight,
        Double weeklyGoal
) {
}
