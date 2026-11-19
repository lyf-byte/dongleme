package com.dongleme.backend.dto;

import java.util.List;

public record RankView(List<RankItem> list, RankMe me) {
}
