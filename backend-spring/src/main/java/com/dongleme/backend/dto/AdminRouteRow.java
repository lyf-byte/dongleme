package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminRouteRow {

    private Long id;

    private String userId;

    private String nickname;

    private String type;

    private LocalDate date;

    private Integer distanceM;

    private Integer durationSec;

    private Integer calories;

    private Integer pointCount;

    private LocalDateTime createdAt;

    private String preview;

    private Long total;
}
