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
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.entity.WeeklySummary;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.SummaryMapper;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProfileService {

    private static final Pattern NICKNAME_RE =
            Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_-]{1,12}$");

    private final AuthService authService;
    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;
    private final SummaryMapper summaryMapper;

    public ProfileService(
            AuthService authService,
            UserMapper userMapper,
            CheckinRecordMapper recordMapper,
            SummaryMapper summaryMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.summaryMapper = summaryMapper;
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
        int weekMinutes = 0;
        Set<LocalDate> weekDays = new LinkedHashSet<>();

        for (CheckinRecord row : rows) {
            int duration = row.getDuration() == null ? 0 : row.getDuration();
            int calories = row.getCalories() == null ? 0 : row.getCalories();
            totalMinutes += duration;
            totalCalories += calories;

            LocalDate date = row.getDate();
            if (date == null) {
                continue;
            }
            if (!date.isBefore(weekStart)) {
                weekMinutes += duration;
                weekDays.add(date);
            }
            if (today.equals(date)) {
                todayMinutes += duration;
                todayCalories += calories;
            }
        }

        ProfileBasicView basic = ProfileBasicView.of(user);
        return new ProfileView(
                basic.nickname(),
                basic.weight(),
                basic.weeklyGoal(),
                basic.streak(),
                basic.createdAt(),
                weekMinutes,
                List.copyOf(weekDays),
                new ProfileView.Today(todayMinutes, todayCalories),
                new ProfileView.Totals(rows.size(), totalMinutes, totalCalories)
        );
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

        authService.requireUser(userId);
        User patch = new User();
        patch.setId(userId);
        patch.setNickname(nickname);
        patch.setWeight(BigDecimal.valueOf(weight));
        patch.setWeeklyGoal(weeklyGoal.intValue());
        userMapper.updateById(patch);

        Object weightValue = weight % 1 == 0 ? weight.intValue() : weight;
        return Map.of(
                "nickname", nickname,
                "weight", weightValue,
                "weeklyGoal", weeklyGoal.intValue()
        );
    }

    @Transactional
    public Map<String, Object> clear(String userId) {
        authService.requireUser(userId);

        recordMapper.delete(
                Wrappers.<CheckinRecord>lambdaQuery().eq(CheckinRecord::getUserId, userId));
        summaryMapper.delete(
                Wrappers.<WeeklySummary>lambdaQuery().eq(WeeklySummary::getUserId, userId));
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getStreak, 0)
                .set(User::getLastCheckDate, null));

        return Map.of("ok", true);
    }

    private List<CheckinRecord> loadRecords(String userId) {
        return recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .orderByAsc(CheckinRecord::getDate));
    }
}
