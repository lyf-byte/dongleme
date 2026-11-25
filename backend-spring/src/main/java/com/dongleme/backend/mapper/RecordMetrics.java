package com.dongleme.backend.mapper;

/**
 * 打卡有效时长口径：待审核记录不计入，其余记录按原始时长。
 * 首页今日数据、个人累计、统计、周小结、排行榜、好友、后台统一使用这一表达式。
 */
public final class RecordMetrics {

    public static final String EFFECTIVE_MINUTES =
            "case when r.flagged = 1 then 0 else r.duration end";

    public static final String EFFECTIVE_CALORIES =
            "case when r.flagged = 1 then 0 else r.calories end";

    private RecordMetrics() {
    }
}
