package com.dongleme.backend.dto;

public record AuthRequest(String action, String account, String password) {
}
