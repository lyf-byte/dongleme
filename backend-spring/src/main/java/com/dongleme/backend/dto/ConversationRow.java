package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationRow {
    private String userId;
    private String nickname;
    private String remark;
    private String gender;
    private String lastContent;
    private LocalDateTime lastAt;
    private Integer unread;
    private Boolean mutual;
    private Boolean pinned;
    private Boolean muted;
}
