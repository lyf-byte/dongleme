package com.dongleme.backend.service;

import com.dongleme.backend.dto.NotificationRow;
import com.dongleme.backend.dto.NotificationView;
import com.dongleme.backend.entity.Notification;
import com.dongleme.backend.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final int LIST_LIMIT = 30;

    private final NotificationMapper notificationMapper;
    private final SseService sseService;

    public NotificationService(NotificationMapper notificationMapper, SseService sseService) {
        this.notificationMapper = notificationMapper;
        this.sseService = sseService;
    }

    /** 有人关注你时生成一条提示；同一人重复关注不重复提醒。 */
    public void notifyFollow(String actorId, String userId) {
        if (actorId == null || userId == null || actorId.equals(userId)) {
            return;
        }
        if (notificationMapper.countUnreadFollowFrom(userId, actorId) > 0) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setActorId(actorId);
        notification.setType("follow");
        notificationMapper.insert(notification);
        sseService.send(userId, "notification",
                Map.of("type", "follow", "actorId", actorId));
    }

    public List<NotificationView> list(String me) {
        return notificationMapper.selectForUser(me, LIST_LIMIT).stream()
                .map(NotificationView::of)
                .toList();
    }

    public long unread(String me) {
        return notificationMapper.countUnread(me);
    }

    public void markRead(String me, Long id) {
        if (id == null) {
            notificationMapper.markAllRead(me);
        } else {
            notificationMapper.markRead(me, id);
        }
    }
}
