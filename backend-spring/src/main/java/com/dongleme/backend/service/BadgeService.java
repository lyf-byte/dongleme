package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.dto.BadgeUnlockRequest;
import com.dongleme.backend.entity.UserBadge;
import com.dongleme.backend.mapper.UserBadgeMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class BadgeService {

    private static final int MAX_BATCH = 50;
    private static final int MAX_ID_LENGTH = 64;

    private final UserBadgeMapper badgeMapper;

    public BadgeService(UserBadgeMapper badgeMapper) {
        this.badgeMapper = badgeMapper;
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

    public List<String> unlock(String userId, BadgeUnlockRequest request) {
        List<String> badgeIds = request == null || request.ids() == null
                ? List.of()
                : request.ids().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(id -> !id.isEmpty() && id.length() <= MAX_ID_LENGTH)
                        .distinct()
                        .limit(MAX_BATCH)
                        .toList();

        if (!badgeIds.isEmpty()) {
            try {
                badgeMapper.insertIgnore(userId, badgeIds);
            } catch (DataAccessException ignored) {
                // 并发或重复上报时忽略，最终以服务端列表为准
            }
        }
        return ids(userId);
    }
}
