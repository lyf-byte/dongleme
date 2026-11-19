package com.dongleme.backend.dto;

import java.util.List;

public record AdminUsersView(
        List<AdminUserView> users,
        long total,
        int page,
        int pageSize
) {
}
