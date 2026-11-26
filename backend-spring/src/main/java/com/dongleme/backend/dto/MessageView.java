package com.dongleme.backend.dto;

import com.dongleme.backend.entity.Message;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record MessageView(
        long id,
        boolean mine,
        String type,
        String content,
        String mediaUrl,
        String payloadJson,
        boolean recalled,
        boolean recallable,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime readAt
) {

    public static MessageView of(Message message, String me) {
        boolean recalled = message.getRecalledAt() != null;
        boolean mine = me.equals(message.getSenderId());
        boolean recallable = mine
                && !recalled
                && message.getCreatedAt() != null
                && message.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(2));
        return new MessageView(
                message.getId(),
                mine,
                message.getType() == null ? "text" : message.getType(),
                recalled ? null : message.getContent(),
                recalled ? null : message.getMediaUrl(),
                recalled ? null : message.getPayloadJson(),
                recalled,
                recallable,
                message.getCreatedAt(),
                message.getReadAt());
    }
}
