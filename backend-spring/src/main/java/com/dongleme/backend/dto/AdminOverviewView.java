package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminOverviewView(
        long totalUsers,
        long totalRecords,
        long activeToday,
        long weekMinutes,
        long todayRecords,
        long weekNewUsers,
        long routeCount,
        List<TrendPoint> trend,
        List<TypeStat> types,
        List<AdminRecentView> recent
) {

    public record TrendPoint(LocalDate date, long records, long users, long newUsers) {
    }

    public record TypeStat(String name, long count, long minutes) {
    }
}
