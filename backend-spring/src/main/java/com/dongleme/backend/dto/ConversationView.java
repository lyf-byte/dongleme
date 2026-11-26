package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ConversationView(
        String userId,
        String nickname,
        String remark,
        String gender,
        String lastContent,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastAt,
        int unread,
        boolean mutual,
        boolean pinned,
        boolean muted
) {
}
