package com.dongleme.backend.dto;

public record AuthUpdateRequest(
        String action,
        Boolean onboarded,
        String oldPassword,
        String newPassword
) {
}
