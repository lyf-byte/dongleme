package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryView(
        LocalDate weekStart,
        @JsonInclude(JsonInclude.Include.ALWAYS) String content,
        boolean cached,
        Boolean degraded,
        Boolean empty
) {
}
