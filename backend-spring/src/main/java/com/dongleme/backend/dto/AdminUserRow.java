package com.dongleme.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminUserRow {

    private String id;

    private String nickname;

    private BigDecimal weight;

    private Integer weeklyGoal;

    private Integer streak;

    private Boolean admin;

    private LocalDateTime createdAt;

    private String email;

    private String phone;

    private String username;

    private Long recordCount;
}
