package com.dongleme.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminUserRow {

    private String id;

    private String nickname;

    private String gender;

    private BigDecimal weight;

    private Integer weeklyGoal;

    private Integer streak;

    private LocalDate lastCheckDate;

    private Boolean admin;

    private LocalDateTime createdAt;

    private String email;

    private String phone;

    private String username;

    private Long recordCount;

    private Long weekMinutes;

    private Long routeCount;
}
