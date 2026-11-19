package com.dongleme.backend.dto;

import java.time.LocalDate;

public record CheckinResult(LocalDate date, int duration, int calories, int streak) {
}
