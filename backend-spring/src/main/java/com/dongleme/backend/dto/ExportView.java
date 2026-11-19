package com.dongleme.backend.dto;

import java.util.List;

public record ExportView(
        ProfileBasicView profile,
        List<RecordView> records,
        List<SummaryExportView> summaries
) {
}
