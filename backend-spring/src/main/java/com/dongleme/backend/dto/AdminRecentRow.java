package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminRecentRow {

    private Long id;

    private LocalDate date;

    private String type;

    private Integer duration;

    private Integer calories;

    private String nickname;
}
