package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StatsView(
        String range,
        LocalDate start,
        LocalDate end,
        List<StatsDaily> daily,
        Map<String, Integer> types,
        StatsTotals total
) {
}
