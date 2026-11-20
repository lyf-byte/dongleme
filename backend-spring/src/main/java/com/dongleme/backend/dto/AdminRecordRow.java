package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminRecordRow {

    private Long id;

    private LocalDate date;

    private String type;

    private Integer duration;

    private Integer calories;

    private String userId;

    private String nickname;

    private Long total;
}
