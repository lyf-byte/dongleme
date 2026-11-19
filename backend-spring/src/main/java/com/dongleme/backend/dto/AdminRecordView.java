package com.dongleme.backend.dto;

import java.time.LocalDate;

public record AdminRecordView(
        long id,
        LocalDate date,
        String type,
        int duration,
        int calories,
        String userId,
        String nickname
) {
}
