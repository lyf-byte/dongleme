package com.dongleme.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时区与周边界：所有日期都必须按 Asia/Shanghai 生成，
 * 否则凌晨 00:00–08:00 的打卡会被算到前一天。
 */
class ShanghaiTimeTest {

    @Test
    @DisplayName("today() 使用 Asia/Shanghai，而不是服务器默认时区")
    void todayUsesShanghaiZone() {
        assertThat(ShanghaiTime.ZONE).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(ShanghaiTime.today()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @DisplayName("上海 00:30 已经是新的一天，UTC 还停在前一天")
    void justAfterMidnightBelongsToNewDay() {
        // 2026-11-20 00:30 +08:00 == 2026-11-19 16:30 UTC
        LocalDate shanghaiDate = LocalDate.of(2026, 11, 20);
        LocalDate utcDate = java.time.LocalDateTime
                .of(2026, 11, 20, 0, 30)
                .atZone(ShanghaiTime.ZONE)
                .withZoneSameInstant(ZoneId.of("UTC"))
                .toLocalDate();

        assertThat(utcDate).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(shanghaiDate).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("23:59 仍属于当天，跨过 0 点才进入下一天")
    void lastMinuteOfDayStaysInSameDay() {
        var at = java.time.LocalDateTime.of(2026, 11, 20, 23, 59, 59);

        assertThat(at.atZone(ShanghaiTime.ZONE).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(at.plusSeconds(1).atZone(ShanghaiTime.ZONE).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    @DisplayName("weekStart 把一周内任意一天都收敛到周一")
    void weekStartAlwaysReturnsMonday() {
        // 2026-11-16 是周一，2026-11-22 是周日
        LocalDate monday = LocalDate.of(2026, 11, 16);
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            assertThat(ShanghaiTime.weekStart(day))
                    .as("weekStart(%s)", day)
                    .isEqualTo(monday);
        }
    }

    @Test
    @DisplayName("weekStart 跨月跨年也正确")
    void weekStartAcrossMonthBoundary() {
        // 2027-01-01 是周五，所在周周一为 2026-12-28
        assertThat(ShanghaiTime.weekStart(LocalDate.of(2027, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 12, 28));
        // 2026-03-01 是周日，所在周周一为 2026-02-23
        assertThat(ShanghaiTime.weekStart(LocalDate.of(2026, 3, 1)))
                .isEqualTo(LocalDate.of(2026, 2, 23));
    }

    @Test
    @DisplayName("effectiveStreak：没打过卡为 0")
    void effectiveStreakWithoutHistory() {
        assertThat(ShanghaiTime.effectiveStreak(5, null)).isZero();
    }

    @Test
    @DisplayName("effectiveStreak：今天或昨天打过卡，保留原值")
    void effectiveStreakKeepsRecentValue() {
        LocalDate today = ShanghaiTime.today();
        assertThat(ShanghaiTime.effectiveStreak(7, today)).isEqualTo(7);
        assertThat(ShanghaiTime.effectiveStreak(7, today.minusDays(1))).isEqualTo(7);
    }

    @Test
    @DisplayName("effectiveStreak：断卡两天及以上归零")
    void effectiveStreakResetsAfterGap() {
        LocalDate today = ShanghaiTime.today();
        assertThat(ShanghaiTime.effectiveStreak(7, today.minusDays(2))).isZero();
        assertThat(ShanghaiTime.effectiveStreak(7, today.minusDays(30))).isZero();
    }

    @Test
    @DisplayName("effectiveStreak：streak 字段为 null 时按 0 处理，不抛 NPE")
    void effectiveStreakHandlesNullStreak() {
        LocalDate today = ShanghaiTime.today();
        assertThat(ShanghaiTime.effectiveStreak(null, today)).isZero();
    }
}
