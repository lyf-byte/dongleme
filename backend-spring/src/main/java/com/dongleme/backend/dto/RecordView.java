package com.dongleme.backend.dto;

import com.dongleme.backend.entity.CheckinRecord;

import java.time.LocalDate;

public record RecordView(LocalDate date, String type, Integer duration, Integer calories) {

    public static RecordView of(CheckinRecord record) {
        return new RecordView(
                record.getDate(),
                record.getType(),
                record.getDuration(),
                record.getCalories()
        );
    }
}
