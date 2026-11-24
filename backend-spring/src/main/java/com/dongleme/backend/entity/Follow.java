package com.dongleme.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follows")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("follower_id")
    private String followerId;

    @TableField("followee_id")
    private String followeeId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
