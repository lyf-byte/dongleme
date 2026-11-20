package com.dongleme.backend.dto;

import com.dongleme.backend.entity.ActivityRoute;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RouteSummaryView(
        Long id,
        LocalDate date,
        String type,
        int distanceM,
        int durationSec,
        int calories,
        LocalDateTime startedAt,
        int pointCount,
        List<double[]> preview
) {

    public static RouteSummaryView of(ActivityRoute route, ObjectMapper objectMapper) {
        return new RouteSummaryView(
                route.getId(),
                route.getDate(),
                route.getType(),
                route.getDistanceM() == null ? 0 : route.getDistanceM(),
                route.getDurationSec() == null ? 0 : route.getDurationSec(),
                route.getCalories() == null ? 0 : route.getCalories(),
                route.getStartedAt(),
                route.getPointCount() == null ? 0 : route.getPointCount(),
                parsePreview(route.getPreview(), objectMapper));
    }

    private static List<double[]> parsePreview(String preview, ObjectMapper objectMapper) {
        if (preview == null || preview.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(preview, new TypeReference<List<double[]>>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
