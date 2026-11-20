package com.dongleme.backend.dto;

import lombok.Data;

@Data
public class AdminUserStatsRow {

    private Long recordCount;

    private Long weekMinutes;

    private Long routeCount;
}
