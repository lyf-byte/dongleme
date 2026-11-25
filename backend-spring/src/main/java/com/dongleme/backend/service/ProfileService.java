package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.ExportView;
import com.dongleme.backend.dto.ProfileBasicView;
import com.dongleme.backend.dto.ProfileUpdateRequest;
import com.dongleme.backend.dto.ProfileView;
import com.dongleme.backend.dto.RecordView;
import com.dongleme.backend.dto.SummaryExportView;
import com.dongleme.backend.entity.ActivityRoute;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.MakeupCard;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.entity.WeeklySummary;
import com.dongleme.backend.mapper.ActivityRouteMapper;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.MakeupCardMapper;
import com.dongleme.backend.mapper.RecordMetrics;
import com.dongleme.backend.mapper.SummaryMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.UserLookupCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProfileService {

    private static final Pattern NICKNAME_RE =
            Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_-]{1,12}$");
    private static final Set<String> GENDERS = Set.of("male", "female", "secret");

    private final AuthService authService;
    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;
    private final SummaryMapper summaryMapper;
    private final ActivityRouteMapper routeMapper;
    private final MakeupCardMapper makeupCardMapper;
    private final UserLookupCache userLookupCache;

    public ProfileService(
            AuthService authService,
            UserMapper userMapper,
            CheckinRecordMapper recordMapper,
            SummaryMapper summaryMapper,
            ActivityRouteMapper routeMapper,
            MakeupCardMapper makeupCardMapper,
            UserLookupCache userLookupCache) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.summaryMapper = summaryMapper;
        this.routeMapper = routeMapper;
        this.makeupCardMapper = makeupCardMapper;
        this.userLookupCache = userLookupCache;
    }

    public ProfileView getProfile(String userId) {
        User user = authService.requireUser(userId);
        List<CheckinRecord> rows = loadRecords(userId);

        LocalDate today = ShanghaiTime.today();
        LocalDate weekStart = ShanghaiTime.weekStart(today);

        int totalMinutes = 0;
        int totalCalories = 0;
        int todayMinutes = 0;
        int todayCalories = 0;
        int todayPendingMinutes = 0;
        int todayPendingCalories = 0;
        int weekMinutes = 0;
        Set<LocalDate> weekDays = new LinkedHashSet<>();
        Set<String> todayTypes = new LinkedHashSet<>();
        Map<LocalDate, DayTotals> days = new HashMap<>();

        for (CheckinRecord row : rows) {
            LocalDate date = row.getDate();
            if (date == null) {
                continue;
            }
            if (!date.isBefore(weekStart)) {
                weekDays.add(date);
            }
            if (today.equals(date) && row.getType() != null) {
                todayTypes.add(row.getType());
            }
            if (row.getFlagged() != null && row.getFlagged() == 1) {
                continue;
            }
            DayTotals day = days.computeIfAbsent(date, key -> new DayTotals());
            int duration = row.getDuration() == null ? 0 : row.getDuration();
            int calories = row.getCalories() == null ? 0 : row.getCalories();
            if (row.getVerified() != null && row.getVerified() == 1) {
                day.routeMinutes += duration;
                day.routeCalories += calories;
            } else {
                day.manualMinutes += duration;
                day.manualCalories += calories;
            }
        }

        for (DayTotals day : days.values()) {
            totalMinutes += effectiveMinutes(day);
            totalCalories += effectiveCalories(day);
        }
        DayTotals todayTotals = days.get(today);
        if (todayTotals != null) {
            todayMinutes = effectiveMinutes(todayTotals);
            todayCalories = effectiveCalories(todayTotals);
            todayPendingMinutes = Math.max(0,
                    todayTotals.manualMinutes - RecordMetrics.DAILY_MANUAL_CAP);
            todayPendingCalories = Math.max(0,
                    todayTotals.manualCalories - cappedManualCalories(todayTotals));
        }

        // 补签日期只算打卡天，不计入时长与热量
        makeupCardMapper.selectList(
                        Wrappers.<MakeupCard>lambdaQuery()
                                .eq(MakeupCard::getUserId, userId))
                .stream()
                .map(MakeupCard::getTargetDate)
                .filter(date -> date != null && !date.isBefore(weekStart))
                .forEach(weekDays::add);

        // 周时长与排行榜、好友、后台统一使用「每日手动封顶 + 轨迹全额」口径
        for (Map.Entry<LocalDate, DayTotals> entry : days.entrySet()) {
            if (!entry.getKey().isBefore(weekStart)) {
                weekMinutes += effectiveMinutes(entry.getValue());
            }
        }
        ProfileBasicView basic = ProfileBasicView.of(user);
        List<ProfileView.RecentRecord> recent = rows.stream()
                .filter(row -> row.getId() != null)
                .sorted(Comparator.comparing(CheckinRecord::getId).reversed())
                .limit(5)
                .map(row -> new ProfileView.RecentRecord(
                        row.getDate(),
                        row.getType(),
                        row.getDuration() == null ? 0 : row.getDuration(),
                        row.getCalories() == null ? 0 : row.getCalories(),
                        row.getFlagged() == null ? 0 : row.getFlagged()))
                .toList();
        return new ProfileView(
                basic.nickname(),
                basic.gender(),
                basic.weight(),
                basic.weeklyGoal(),
                user.getNextWeeklyGoal(),
                basic.streak(),
                basic.createdAt(),
                weekMinutes,
                List.copyOf(weekDays),
                new ProfileView.Today(
                        todayMinutes,
                        todayCalories,
                        todayPendingMinutes,
                        todayPendingCalories,
                        List.copyOf(todayTypes)),
                new ProfileView.Totals(rows.size(), totalMinutes, totalCalories),
                recent
        );
    }

    private static int effectiveMinutes(DayTotals day) {
        return day.routeMinutes + Math.min(RecordMetrics.DAILY_MANUAL_CAP, day.manualMinutes);
    }

    /** 手动部分按有效分钟占比折算热量，超出每日上限的部分不计 */
    private static int cappedManualCalories(DayTotals day) {
        if (day.manualMinutes <= 0) {
            return 0;
        }
        int capped = Math.min(RecordMetrics.DAILY_MANUAL_CAP, day.manualMinutes);
        if (capped >= day.manualMinutes) {
            return day.manualCalories;
        }
        return (int) Math.round(day.manualCalories * (capped / (double) day.manualMinutes));
    }

    private static int effectiveCalories(DayTotals day) {
        return day.routeCalories + cappedManualCalories(day);
    }

    private static final class DayTotals {
        private int routeMinutes;
        private int manualMinutes;
        private int routeCalories;
        private int manualCalories;
    }

    public ExportView export(String userId) {
        User user = authService.requireUser(userId);
        List<CheckinRecord> rows = loadRecords(userId);
        List<WeeklySummary> summaries = summaryMapper.selectList(
                Wrappers.<WeeklySummary>lambdaQuery()
                        .eq(WeeklySummary::getUserId, userId)
                        .orderByAsc(WeeklySummary::getWeekStart));

        return new ExportView(
                ProfileBasicView.of(user),
                rows.stream().map(RecordView::of).toList(),
                summaries.stream().map(SummaryExportView::of).toList()
        );
    }

    public Map<String, Object> update(String userId, ProfileUpdateRequest request) {
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        if (!NICKNAME_RE.matcher(nickname).matches()) {
            throw ApiException.badRequest("昵称需为 1–12 位中英文、数字、下划线或连字符");
        }

        User current = authService.requireUser(userId);
        String gender = request.gender() == null ? "" : request.gender().trim();
        if (gender.isEmpty()) {
            gender = current.getGender() == null ? "secret" : current.getGender();
        }
        if (!GENDERS.contains(gender)) {
            throw ApiException.badRequest("性别参数不合法");
        }

        Double weight = request.weight();
        if (weight == null || !Double.isFinite(weight) || weight < 20 || weight > 300) {
            throw ApiException.badRequest("体重需在 20–300 kg 之间");
        }

        Double weeklyGoal = request.weeklyGoal();
        if (weeklyGoal == null
                || !Double.isFinite(weeklyGoal)
                || weeklyGoal != Math.rint(weeklyGoal)
                || weeklyGoal < 1
                || weeklyGoal > 2000) {
            throw ApiException.badRequest("周目标需为 1–2000 的整数");
        }

        User patch = new User();
        patch.setId(userId);
        patch.setNickname(nickname);
        patch.setGender(gender);
        patch.setWeight(BigDecimal.valueOf(weight));
        patch.setWeeklyGoal(weeklyGoal.intValue());
        userMapper.updateById(patch);
        userLookupCache.invalidate(userId);

        Object weightValue = weight % 1 == 0 ? weight.intValue() : weight;
        return Map.of(
                "nickname", nickname,
                "gender", gender,
                "weight", weightValue,
                "weeklyGoal", weeklyGoal.intValue()
        );
    }

    /**
     * 把 AI 小结给出的目标存为「下周目标」，到下周一时由 requireUser 自动切换为当前周目标。
     */
    public Map<String, Object> setNextWeeklyGoal(String userId, Integer weeklyGoal) {
        if (weeklyGoal == null || weeklyGoal < 1 || weeklyGoal > 2000) {
            throw ApiException.badRequest("周目标需为 1–2000 的整数");
        }
        authService.requireUser(userId);

        LocalDate nextWeek = ShanghaiTime.weekStart(ShanghaiTime.today()).plusWeeks(1);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getNextWeeklyGoal, weeklyGoal)
                .set(User::getNextGoalWeek, nextWeek));
        userLookupCache.invalidate(userId);

        return Map.of("nextWeeklyGoal", weeklyGoal, "nextGoalWeek", nextWeek.toString());
    }

    @Transactional
    public Map<String, Object> clear(String userId) {
        authService.requireUser(userId);

        routeMapper.delete(
                Wrappers.<ActivityRoute>lambdaQuery().eq(ActivityRoute::getUserId, userId));
        recordMapper.delete(
                Wrappers.<CheckinRecord>lambdaQuery().eq(CheckinRecord::getUserId, userId));
        summaryMapper.delete(
                Wrappers.<WeeklySummary>lambdaQuery().eq(WeeklySummary::getUserId, userId));
        makeupCardMapper.delete(
                Wrappers.<MakeupCard>lambdaQuery().eq(MakeupCard::getUserId, userId));
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getStreak, 0)
                .set(User::getLastCheckDate, null));
        userLookupCache.invalidate(userId);

        return Map.of("ok", true);
    }

    private List<CheckinRecord> loadRecords(String userId) {
        return recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .orderByAsc(CheckinRecord::getDate));
    }
}
