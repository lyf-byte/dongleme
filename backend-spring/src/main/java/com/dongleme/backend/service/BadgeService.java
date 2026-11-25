package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.dto.BadgeUnlockRequest;
import com.dongleme.backend.dto.ProfileView;
import com.dongleme.backend.entity.UserBadge;
import com.dongleme.backend.mapper.UserBadgeMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BadgeService {

    private final UserBadgeMapper badgeMapper;
    private final ProfileService profileService;

    public BadgeService(UserBadgeMapper badgeMapper, ProfileService profileService) {
        this.badgeMapper = badgeMapper;
        this.profileService = profileService;
    }

    public List<String> ids(String userId) {
        return badgeMapper.selectObjs(
                        Wrappers.<UserBadge>query()
                                .select("badge_id")
                                .eq("user_id", userId)
                                .orderByAsc("id"))
                .stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    /**
     * 客户端上报的 ids 不再作为可信来源，仅保留接口兼容。
     * 勋章是否解锁完全由服务端根据当前统计数据判定，客户端无法伪造。
     */
    public List<String> unlock(String userId, BadgeUnlockRequest request) {
        List<String> earned = earnedIds(profileService.getProfile(userId));
        if (!earned.isEmpty()) {
            try {
                badgeMapper.insertIgnore(userId, earned);
            } catch (DataAccessException ignored) {
                // 并发或重复上报时忽略，最终以服务端列表为准
            }
        }
        return ids(userId);
    }

    /** 规则与前端 utils/badges.js 保持一致，只写入服务端判定已达成的 id。 */
    private static List<String> earnedIds(ProfileView profile) {
        int goal = profile.weeklyGoal() == null ? 150 : profile.weeklyGoal();
        ProfileView.Totals total = profile.total();
        int count = total == null ? 0 : total.count();
        int minutes = total == null ? 0 : total.minutes();
        int calories = total == null ? 0 : total.calories();
        int weekDays = profile.weekDays() == null ? 0 : profile.weekDays().size();

        List<String> earned = new ArrayList<>();
        if (count >= 1) {
            earned.add("first-checkin");
        }
        if (count >= 10) {
            earned.add("count-master");
        }
        if (profile.streak() >= 3) {
            earned.add("streak-fire");
        }
        if (minutes >= 100) {
            earned.add("time-keeper");
        }
        if (calories >= 1000) {
            earned.add("calorie-burner");
        }
        if (goal > 0 && profile.weekMinutes() >= goal) {
            earned.add("week-goal");
        }
        if (weekDays >= 7) {
            earned.add("week-full");
        }
        if (goal >= 300) {
            earned.add("ambition");
        }
        return earned;
    }
}
