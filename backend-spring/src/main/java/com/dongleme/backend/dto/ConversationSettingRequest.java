package com.dongleme.backend.dto;

public record ConversationSettingRequest(String peerId, Boolean pinned, Boolean muted) {
}
