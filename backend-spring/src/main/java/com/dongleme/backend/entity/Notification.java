package com.dongleme.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("actor_id")
    private String actorId;

    private String type;

    private String content;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
