package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record NotificationView(
        long id,
        String type,
        String actorId,
        String nickname,
        String remark,
        String gender,
        String content,
        boolean read,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
) {

    public static NotificationView of(NotificationRow row) {
        return new NotificationView(
                row.getId() == null ? 0 : row.getId(),
                row.getType(),
                row.getActorId(),
                row.getNickname(),
                row.getRemark(),
                row.getGender(),
                row.getContent(),
                row.getReadAt() != null,
                row.getCreatedAt());
    }
}
