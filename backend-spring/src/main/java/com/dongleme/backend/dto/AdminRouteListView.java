package com.dongleme.backend.dto;

import java.util.List;

public record AdminRouteListView(
        List<AdminRouteView> routes,
        long total,
        int page,
        int pageSize
) {
}
