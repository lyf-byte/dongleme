package com.dongleme.backend.dto;

import java.util.List;

public record AdminUserDetailView(
        AdminUserView user,
        List<AdminRecordView> records,
        List<AdminRouteView> routes
) {
}
