package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.FollowFeedRow;
import com.dongleme.backend.dto.FollowRow;
import com.dongleme.backend.entity.Follow;
import com.dongleme.backend.mapper.FollowMapper;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FollowService {

    private static final int FEED_DAYS = 14;
    private static final int FEED_LIMIT = 30;

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public FollowService(
            FollowMapper followMapper,
            UserMapper userMapper,
            NotificationService notificationService) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    public List<FollowRow> following(String userId) {
        return followMapper.selectFollowing(userId, ShanghaiTime.weekStart(ShanghaiTime.today()));
    }

    public List<FollowRow> mutual(String userId) {
        return followMapper.selectMutual(userId);
    }

    public List<FollowFeedRow> feed(String userId) {
        return followMapper.selectFeed(
                userId,
                ShanghaiTime.today().minusDays(FEED_DAYS),
                FEED_LIMIT);
    }

    public Map<String, Object> follow(String userId, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw ApiException.badRequest("缺少用户 id");
        }
        if (targetId.equals(userId)) {
            throw ApiException.badRequest("不能关注自己");
        }
        if (userMapper.selectById(targetId) == null) {
            throw ApiException.notFound("用户不存在");
        }

        Follow follow = new Follow();
        follow.setFollowerId(userId);
        follow.setFolloweeId(targetId);
        try {
            followMapper.insert(follow);
        } catch (DuplicateKeyException ignored) {
            // 重复关注按成功处理
        }
        notificationService.notifyFollow(userId, targetId);
        return Map.of("ok", true);
    }

    public Map<String, Object> unfollow(String userId, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw ApiException.badRequest("缺少用户 id");
        }
        followMapper.delete(
                Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getFollowerId, userId)
                        .eq(Follow::getFolloweeId, targetId));
        return Map.of("ok", true);
    }
}
