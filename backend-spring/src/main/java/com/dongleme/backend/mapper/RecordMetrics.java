package com.dongleme.backend.mapper;

/**
 * 打卡统计口径：待审核(flagged)记录不计；手动/计时器每天有效封顶 240 分钟，GPS 轨迹全额。
 * 首页、个人累计、统计、周小结、排行榜、好友、后台统一使用这一口径。
 */
public final class RecordMetrics {

    /** 每日手动/计时器有效时长上限（分钟），GPS 轨迹不占此额度 */
    public static final int DAILY_MANUAL_CAP = 240;

    public static final String EFFECTIVE_MINUTES =
            "case when r.flagged = 1 then 0 else r.duration end";

    public static final String EFFECTIVE_CALORIES =
            "case when r.flagged = 1 then 0 else r.calories end";

    /**
     * 按「用户 + 日期」聚合的每日有效分钟：轨迹全额，手动/计时器每天封顶 240。
     * 供排行榜、好友、后台等 SQL 作为子查询使用（别名必须是 d）。
     */
    public static final String EFFECTIVE_DAY_MINUTES_SQL =
            "select r.user_id, r.date, "
                    + "sum(case when r.flagged = 1 then 0 when r.verified = 1 then r.duration else 0 end) "
                    + "+ least(sum(case when r.flagged = 1 then 0 when r.verified = 1 then 0 "
                    + "else r.duration end), " + DAILY_MANUAL_CAP + ") as day_minutes "
                    + "from records r group by r.user_id, r.date";

    private RecordMetrics() {
    }
}
