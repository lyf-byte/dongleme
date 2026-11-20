package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RankItem(
        String id,
        String nickname,
        String gender,
        long weeklyMinutes,
        @JsonProperty("isMe") boolean isMe
) {
}
