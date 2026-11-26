package com.dongleme.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("sender_id")
    private String senderId;

    @TableField("receiver_id")
    private String receiverId;

    private String type;

    private String content;

    @TableField("media_url")
    private String mediaUrl;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("recalled_at")
    private LocalDateTime recalledAt;

    @TableField("sender_deleted")
    private Boolean senderDeleted;

    @TableField("receiver_deleted")
    private Boolean receiverDeleted;
}
