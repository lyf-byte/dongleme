package com.dongleme.backend.service;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.CheckinRequest;
import com.dongleme.backend.dto.CheckinResult;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.CustomSport;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.CustomSportMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.UserLookupCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    private static final String USER_ID = "user-1";

    @Mock
    private UserMapper userMapper;
    @Mock
    private CheckinRecordMapper recordMapper;
    @Mock
    private CustomSportMapper customSportMapper;
    @Mock
    private UserLookupCache userLookupCache;

    private CheckinService service;

    @BeforeEach
    void setUp() {
        service = new CheckinService(userMapper, recordMapper, customSportMapper, userLookupCache);
    }

    private User user(String gender, String weight, Integer streak, LocalDate lastCheckDate) {
        User user = new User();
        user.setId(USER_ID);
        user.setGender(gender);
        user.setWeight(weight == null ? null : new BigDecimal(weight));
        user.setStreak(streak);
        user.setLastCheckDate(lastCheckDate);
        return user;
    }

    private CheckinService.CheckinOutcome checkinAt(
            String type, int duration, Double weight, boolean overwrite, LocalDate date) {
        return service.checkinAt(USER_ID, type, duration, weight, overwrite, date);
    }

    @Nested
    @DisplayName("时长校验")
    class DurationValidation {

        @Test
        @DisplayName("小于 1 分钟被拒绝")
        void rejectsZeroDuration() {
            assertThatThrownBy(() -> service.checkin(USER_ID, new CheckinRequest("跑步", 0.0, null, false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("时长");
        }

        @Test
        @DisplayName("超过 600 分钟被拒绝")
        void rejectsTooLongDuration() {
            assertThatThrownBy(() -> service.checkin(USER_ID, new CheckinRequest("跑步", 601.0, null, false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("时长");
        }

        @Test
        @DisplayName("小数时长被拒绝")
        void rejectsFractionalDuration() {
            assertThatThrownBy(() -> service.checkin(USER_ID, new CheckinRequest("跑步", 30.5, null, false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("时长");
        }

        @Test
        @DisplayName("null 时长被拒绝，且不会落库")
        void rejectsNullDuration() {
            assertThatThrownBy(() -> service.checkin(USER_ID, new CheckinRequest("跑步", null, null, false)))
                    .isInstanceOf(ApiException.class);

            verify(recordMapper, never()).insert(any(CheckinRecord.class));
            verify(recordMapper, never()).upsert(any());
        }

        @Test
        @DisplayName("边界值 1 与 600 均可通过")
        void acceptsBoundaryDurations() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(service.checkin(USER_ID, new CheckinRequest("跑步", 1.0, null, false)).duration())
                    .isEqualTo(1);
            assertThat(service.checkin(USER_ID, new CheckinRequest("跑步", 600.0, null, false)).duration())
                    .isEqualTo(600);
        }
    }

    @Nested
    @DisplayName("运动类型与热量")
    class Calories {

        @Test
        @DisplayName("未知运动类型被拒绝")
        void rejectsUnknownType() {
            when(customSportMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> checkinAt("飞行", 30, null, false, LocalDate.of(2026, 11, 20)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("运动类型");
        }

        @Test
        @DisplayName("用户自定义运动按其 MET 计算热量")
        void usesCustomSportMet() {
            CustomSport custom = new CustomSport();
            custom.setId(1L);
            custom.setUserId(USER_ID);
            custom.setName("爬楼梯");
            custom.setMet(new BigDecimal("8.8"));

            when(customSportMapper.selectOne(any())).thenReturn(custom);
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            // 8.8 × 3.5 × 60 × 30 ÷ 200 = 277.2 → 277
            assertThat(checkinAt("爬楼梯", 30, null, false, LocalDate.of(2026, 11, 20)).record().getCalories())
                    .isEqualTo(277);
        }

        @Test
        @DisplayName("跑步 8.0 MET × 60kg × 30 分钟 = 252 千卡")
        void computesRunningCalories() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)).record().getCalories())
                    .isEqualTo(252);
        }

        @Test
        @DisplayName("女性系数 0.9 参与计算：快走 45 分钟 = 168 千卡")
        void appliesFemaleFactor() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("female", "55", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("快走", 45, null, false, LocalDate.of(2026, 11, 20)).record().getCalories())
                    .isEqualTo(168);
        }

        @Test
        @DisplayName("gender 为 secret 时与男性同系数")
        void secretGenderUsesDefaultFactor() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("secret", "70", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跳绳", 20, null, false, LocalDate.of(2026, 11, 20)).record().getCalories())
                    .isEqualTo(301);
        }

        @Test
        @DisplayName("传入体重优先于用户档案体重")
        void explicitWeightOverridesProfile() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "100", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, 60.0, false, LocalDate.of(2026, 11, 20)).record().getCalories())
                    .isEqualTo(252);
        }

        @Test
        @DisplayName("体重超出 20–300 范围被拒绝")
        void rejectsOutOfRangeWeight() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));

            assertThatThrownBy(() -> checkinAt("跑步", 30, 15.0, false, LocalDate.of(2026, 11, 20)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("体重");
            assertThatThrownBy(() -> checkinAt("跑步", 30, 301.0, false, LocalDate.of(2026, 11, 20)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("体重");
        }

        @Test
        @DisplayName("用户不存在时返回 404")
        void rejectsMissingUser() {
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("同日去重")
    class Deduplication {

        @Test
        @DisplayName("同一天同类型重复打卡返回 409")
        void duplicateCheckinConflicts() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenThrow(new DuplicateKeyException("uk_records_user_date_type"));

            assertThatThrownBy(() -> checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                    .hasMessageContaining("已打卡");
        }

        @Test
        @DisplayName("重冲突败时不会更新 streak")
        void duplicateDoesNotTouchStreak() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 3, LocalDate.of(2026, 11, 19)));
            when(recordMapper.insert(any(CheckinRecord.class))).thenThrow(new DuplicateKeyException("dup"));

            assertThatThrownBy(() -> checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)))
                    .isInstanceOf(ApiException.class);

            verify(userMapper, never()).updateById(any(User.class));
            verify(userLookupCache, never()).invalidate(anyString());
        }

        @Test
        @DisplayName("overwrite=true 走 upsert，不触发冲突")
        void overwriteUsesUpsert() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.upsert(any(CheckinRecord.class))).thenReturn(1);

            CheckinResult result = service.checkin(
                    USER_ID, new CheckinRequest("跑步", 45.0, null, true));

            verify(recordMapper).upsert(any(CheckinRecord.class));
            verify(recordMapper, never()).insert(any(CheckinRecord.class));
            assertThat(result.duration()).isEqualTo(45);
        }
    }

    @Nested
    @DisplayName("连续天数 streak")
    class Streak {

        @Test
        @DisplayName("首次打卡 streak = 1")
        void firstCheckinStartsAtOne() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)).streak())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("昨天打过卡，今天 +1")
        void consecutiveDayIncrements() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 5, LocalDate.of(2026, 11, 19)));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)).streak())
                    .isEqualTo(6);
        }

        @Test
        @DisplayName("同一天换一种运动，streak 不变")
        void sameDayDifferentSportKeepsStreak() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 5, LocalDate.of(2026, 11, 20)));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("游泳", 30, null, false, LocalDate.of(2026, 11, 20)).streak())
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("中断两天后从 1 重新开始")
        void gapResetsStreak() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 9, LocalDate.of(2026, 11, 17)));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)).streak())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("跨月连续：11-30 → 12-01 仍然 +1")
        void consecutiveAcrossMonth() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 4, LocalDate.of(2026, 11, 30)));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, null, false, LocalDate.of(2026, 12, 1)).streak())
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("streak 字段为 null 时不抛 NPE")
        void nullStreakTreatedAsZero() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", null, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            assertThat(checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20)).streak())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("打卡成功后写入 streak 与 lastCheckDate，并失效鉴权缓存")
        void persistsStreakAndInvalidatesCache() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 2, LocalDate.of(2026, 11, 19)));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            checkinAt("跑步", 30, null, false, LocalDate.of(2026, 11, 20));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStreak()).isEqualTo(3);
            assertThat(captor.getValue().getLastCheckDate()).isEqualTo(LocalDate.of(2026, 11, 20));
            verify(userLookupCache).invalidate(USER_ID);
        }

        @Test
        @DisplayName("写入的记录日期等于打卡日期，类型原样保存")
        void persistsNormalizedRecord() {
            when(userMapper.selectById(USER_ID)).thenReturn(user("male", "60", 0, null));
            when(recordMapper.insert(any(CheckinRecord.class))).thenReturn(1);

            checkinAt("骑行", 60, null, false, LocalDate.of(2026, 11, 20));

            ArgumentCaptor<CheckinRecord> captor = ArgumentCaptor.forClass(CheckinRecord.class);
            verify(recordMapper).insert(captor.capture());
            assertThat(captor.getValue().getDate()).isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(captor.getValue().getType()).isEqualTo("骑行");
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        }
    }
}
