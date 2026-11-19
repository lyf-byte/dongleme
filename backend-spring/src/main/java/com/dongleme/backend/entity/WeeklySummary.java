package com.dongleme.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("summaries")
public class WeeklySummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("week_start")
    private LocalDate weekStart;

    private String content;

    @TableField("regen_count")
    private Integer regenCount;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
