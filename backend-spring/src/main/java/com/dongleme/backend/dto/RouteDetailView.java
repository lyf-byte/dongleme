package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RouteDetailView(
        Long id,
        LocalDate date,
        String type,
        int distanceM,
        int durationSec,
        int calories,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int pointCount,
        List<RoutePoint> points
) {
}
