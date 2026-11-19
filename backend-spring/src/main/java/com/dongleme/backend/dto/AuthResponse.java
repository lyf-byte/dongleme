package com.dongleme.backend.dto;

public record AuthResponse(String token, UserView user) {
}
