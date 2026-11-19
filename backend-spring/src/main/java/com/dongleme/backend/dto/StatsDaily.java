package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.util.Map;

public record StatsDaily(
        LocalDate date,
        int minutes,
        int calories,
        Map<String, Integer> types
) {
}
