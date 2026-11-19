package com.dongleme.backend.dto;

public record CheckinRequest(
        String type,
        Double duration,
        Double weight,
        Boolean overwrite
) {
}
