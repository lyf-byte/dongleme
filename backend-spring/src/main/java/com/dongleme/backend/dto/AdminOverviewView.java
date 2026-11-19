package com.dongleme.backend.dto;

import java.util.List;

public record AdminOverviewView(
        long totalUsers,
        long totalRecords,
        long activeToday,
        long weekMinutes,
        List<AdminRecentView> recent
) {
}
