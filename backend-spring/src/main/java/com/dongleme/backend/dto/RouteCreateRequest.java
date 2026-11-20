package com.dongleme.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RouteCreateRequest(
        String type,
        Integer durationSec,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        List<RoutePoint> points,
        String clientId,
        String source,
        Boolean overwrite
) {
}
