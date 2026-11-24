package com.dongleme.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.INPUT)
    private String id;

    private String email;

    private String phone;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    private String nickname;

    private String gender;

    private BigDecimal weight;

    @TableField("weekly_goal")
    private Integer weeklyGoal;

    @TableField("next_weekly_goal")
    private Integer nextWeeklyGoal;

    @TableField("next_goal_week")
    private LocalDate nextGoalWeek;

    @TableField("is_admin")
    private Boolean isAdmin;

    private Boolean onboarded;

    private Integer streak;

    @TableField("last_check_date")
    private LocalDate lastCheckDate;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
