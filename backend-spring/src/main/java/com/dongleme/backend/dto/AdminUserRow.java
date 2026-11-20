package com.dongleme.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 后台用户列表行：用户资料与打卡、轨迹统计的聚合结果。
 */
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

    /**
     * 窗口函数带出的筛选结果总数，仅用于分页。
     */
    private Long total;
}
