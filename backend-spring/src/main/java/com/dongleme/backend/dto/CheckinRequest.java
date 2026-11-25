package com.dongleme.backend.dto;

public record CheckinRequest(
        String type,
        Double duration,
        Double weight,
        Boolean overwrite,
        String source
) {

    public CheckinRequest(String type, Double duration, Double weight, Boolean overwrite) {
        this(type, duration, weight, overwrite, null);
    }
}
