package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRouteView(
        long id,
        String userId,
        String nickname,
        String type,
        LocalDate date,
        int distanceM,
        int durationSec,
        int calories,
        int pointCount,
        LocalDateTime createdAt,
        List<double[]> preview
) {
}
