package com.dongleme.backend.dto;

import lombok.Data;

@Data
public class AdminSummaryRow {

    private Long totalUsers;

    private Long totalRecords;

    private Long activeToday;

    private Long todayRecords;

    private Long weekMinutes;

    private Long weekNewUsers;

    private Long routeCount;
}
