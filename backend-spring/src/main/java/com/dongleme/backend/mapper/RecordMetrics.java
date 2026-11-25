package com.dongleme.backend.mapper;

/**
 * 打卡有效时长口径：被标记的记录不计分，已验证记录全额，未验证记录按 50%。
 * 排行榜、个人周进度、好友列表、后台统计统一使用这一表达式。
 */
public final class RecordMetrics {

    public static final String EFFECTIVE_MINUTES =
            "case when r.flagged = 1 then 0 when r.verified = 1 then r.duration else r.duration * 0.5 end";

    private RecordMetrics() {
    }
}
