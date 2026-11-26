package com.dongleme.backend.dto;

import java.util.List;

public record ChatHistoryView(
        String userId,
        String nickname,
        String remark,
        String gender,
        boolean mutual,
        boolean blocked,
        boolean blockedByPeer,
        boolean pinned,
        boolean muted,
        List<MessageView> messages
) {
}
