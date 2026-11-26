package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationRow {
    private Long id;
    private String type;
    private String actorId;
    private String nickname;
    private String remark;
    private String gender;
    private String content;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
