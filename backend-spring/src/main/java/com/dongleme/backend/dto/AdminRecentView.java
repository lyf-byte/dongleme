package com.dongleme.backend.dto;

import java.time.LocalDate;

public record AdminRecentView(
        long id,
        LocalDate date,
        String type,
        int duration,
        int calories,
        String nickname
) {
}
