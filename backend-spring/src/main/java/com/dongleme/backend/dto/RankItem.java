package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RankItem(
        String id,
        String nickname,
        long weeklyMinutes,
        @JsonProperty("isMe") boolean isMe
) {
}
