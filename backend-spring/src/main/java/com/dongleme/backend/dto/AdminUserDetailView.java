package com.dongleme.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailView(
        AdminUserView user,
        List<AdminRecordView> records,
        List<AdminRouteView> routes
) {
}
