package com.dongleme.backend.dto;

import com.dongleme.backend.entity.WeeklySummary;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SummaryExportView(
        @JsonProperty("week_start") LocalDate weekStart,
        String content,
        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
) {

    public static SummaryExportView of(WeeklySummary summary) {
        return new SummaryExportView(
                summary.getWeekStart(),
                summary.getContent(),
                summary.getCreatedAt()
        );
    }
}
