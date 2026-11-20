package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminTrendRow {

    private LocalDate date;

    private Long records;

    private Long users;
}
