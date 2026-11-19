package com.dongleme.backend.dto;

import java.util.List;

public record AdminRecordsView(
        List<AdminRecordView> records,
        long total,
        int page,
        int pageSize
) {
}
