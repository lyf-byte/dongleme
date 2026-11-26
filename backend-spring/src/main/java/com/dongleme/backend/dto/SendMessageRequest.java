package com.dongleme.backend.dto;

public record SendMessageRequest(
        String receiverId,
        String content,
        String type,
        String mediaUrl,
        String payloadJson
) {
}
