package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dongleme.backend.dto.ChatHistoryView;
import com.dongleme.backend.dto.ConversationSettingRow;
import com.dongleme.backend.dto.ConversationView;
import com.dongleme.backend.dto.MessageView;
import com.dongleme.backend.dto.SendMessageRequest;
import com.dongleme.backend.entity.Follow;
import com.dongleme.backend.entity.Message;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.ContactRemarkMapper;
import com.dongleme.backend.mapper.ConversationSettingMapper;
import com.dongleme.backend.mapper.FollowMapper;
import com.dongleme.backend.mapper.MessageMapper;
import com.dongleme.backend.mapper.NotificationMapper;
import com.dongleme.backend.mapper.UserBlockMapper;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 好友私信：只有相互关注的人才能互发消息，规则统一在服务端校验。
 */
@Service
public class MessageService {

    private static final int MAX_LENGTH = 500;
    private static final int HISTORY_LIMIT = 30;
    private static final int RECENT_LIMIT = 5;
    private static final int RECENT_WINDOW_SECONDS = 10;
    private static final int RECALL_WINDOW_MINUTES = 2;
    private static final Set<String> TYPES = Set.of("text", "image", "record", "location");

    private final MessageMapper messageMapper;
    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final ContactRemarkMapper contactRemarkMapper;
    private final UserBlockMapper userBlockMapper;
    private final ConversationSettingMapper conversationSettingMapper;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public MessageService(
            MessageMapper messageMapper,
            NotificationMapper notificationMapper,
            UserMapper userMapper,
            FollowMapper followMapper,
            ContactRemarkMapper contactRemarkMapper,
            UserBlockMapper userBlockMapper,
            ConversationSettingMapper conversationSettingMapper,
            SseService sseService,
            ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.contactRemarkMapper = contactRemarkMapper;
        this.userBlockMapper = userBlockMapper;
        this.conversationSettingMapper = conversationSettingMapper;
        this.sseService = sseService;
        this.objectMapper = objectMapper;
    }

    public List<ConversationView> conversations(String me) {
        return messageMapper.selectConversations(me).stream()
                .map(row -> new ConversationView(
                        row.getUserId(),
                        row.getNickname(),
                        row.getRemark(),
                        row.getGender(),
                        row.getLastContent(),
                        row.getLastAt(),
                        row.getUnread() == null ? 0 : row.getUnread(),
                        Boolean.TRUE.equals(row.getMutual()),
                        Boolean.TRUE.equals(row.getPinned()),
                        Boolean.TRUE.equals(row.getMuted())))
                .toList();
    }

    public ChatHistoryView history(String me, String peer, Long beforeId) {
        User user = peer == null ? null : userMapper.selectById(peer);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        List<Message> rows = messageMapper.selectHistory(me, peer, beforeId, HISTORY_LIMIT);
        List<MessageView> messages = new ArrayList<>();
        for (Message row : rows) {
            messages.add(MessageView.of(row, me));
        }
        // SQL 按 id 倒序查最近 N 条，返回前翻成时间正序，前端可直接追加
        Collections.reverse(messages);
        ConversationSettingRow setting = conversationSettingMapper.select(me, peer);
        return new ChatHistoryView(
                user.getId(),
                user.getNickname(),
                contactRemarkMapper.selectRemark(me, peer),
                user.getGender(),
                isMutual(me, peer),
                userBlockMapper.count(me, peer) > 0,
                userBlockMapper.count(peer, me) > 0,
                setting != null && Boolean.TRUE.equals(setting.getPinned()),
                setting != null && Boolean.TRUE.equals(setting.getMuted()),
                messages);
    }

    public MessageView send(String me, SendMessageRequest request) {
        String receiverId = request == null ? null : request.receiverId();
        String type = request == null || request.type() == null || request.type().isBlank()
                ? "text" : request.type().trim();
        String content = request == null || request.content() == null
                ? "" : request.content().trim();
        String mediaUrl = request == null || request.mediaUrl() == null
                ? "" : request.mediaUrl().trim();
        String payloadJson = request == null || request.payloadJson() == null
                ? "" : request.payloadJson().trim();

        if (receiverId == null || receiverId.isBlank()) {
            throw ApiException.badRequest("缺少接收者");
        }
        if (receiverId.equals(me)) {
            throw ApiException.badRequest("不能给自己发消息");
        }
        if (!TYPES.contains(type)) {
            throw ApiException.badRequest("不支持的消息类型");
        }
        if (content.length() > MAX_LENGTH) {
            throw ApiException.badRequest("消息最多 " + MAX_LENGTH + " 个字");
        }
        if ("text".equals(type) && content.isEmpty()) {
            throw ApiException.badRequest("消息不能为空");
        }
        if ("image".equals(type) && !mediaUrl.startsWith("/uploads/")) {
            throw ApiException.badRequest("图片地址不合法");
        }
        if (mediaUrl.length() > 255 || payloadJson.length() > 2000) {
            throw ApiException.badRequest("消息内容过长");
        }
        if ("location".equals(type)) {
            validateLocation(payloadJson);
        } else if ("record".equals(type)) {
            validateRecord(payloadJson);
        }
        if (userBlockMapper.count(me, receiverId) > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "你已拉黑对方，解除后可继续聊天");
        }
        if (userBlockMapper.count(receiverId, me) > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "对方已将你拉黑");
        }
        if (!isMutual(me, receiverId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "相互关注后才能聊天");
        }
        if (messageMapper.countRecentSent(me, receiverId) >= RECENT_LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "发得太快了，稍后再试");
        }

        Message message = new Message();
        message.setSenderId(me);
        message.setReceiverId(receiverId);
        message.setType(type);
        message.setContent(content);
        message.setMediaUrl(mediaUrl.isEmpty() ? null : mediaUrl);
        message.setPayloadJson(payloadJson.isEmpty() ? null : payloadJson);
        message.setCreatedAt(LocalDateTime.now());
        message.setSenderDeleted(false);
        message.setReceiverDeleted(false);
        messageMapper.insert(message);

        MessageView forReceiver = MessageView.of(message, receiverId);
        MessageView forSender = MessageView.of(message, me);
        sseService.send(receiverId, "message", Map.of("peerId", me, "message", forReceiver));
        sseService.send(me, "message", Map.of("peerId", receiverId, "message", forSender));
        return forSender;
    }

    public Map<String, Object> recall(String me, long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw ApiException.notFound("消息不存在");
        }
        if (!me.equals(message.getSenderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能撤回自己的消息");
        }
        if (message.getRecalledAt() != null) {
            throw ApiException.badRequest("消息已撤回");
        }
        if (message.getCreatedAt() != null
                && message.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(RECALL_WINDOW_MINUTES))) {
            throw ApiException.badRequest("超过 " + RECALL_WINDOW_MINUTES + " 分钟，不能撤回");
        }

        Message patch = new Message();
        patch.setId(messageId);
        patch.setRecalledAt(LocalDateTime.now());
        messageMapper.updateById(patch);

        sseService.send(message.getReceiverId(), "recall",
                Map.of("messageId", messageId, "peerId", me));
        sseService.send(me, "recall",
                Map.of("messageId", messageId, "peerId", message.getReceiverId()));
        return Map.of("ok", true);
    }

    public Map<String, Object> markRead(String me, String peer) {
        if (peer == null || peer.isBlank()) {
            throw ApiException.badRequest("缺少用户 id");
        }
        messageMapper.markReadFrom(me, peer);
        return Map.of("ok", true);
    }

    public Map<String, Object> unread(String me) {
        return Map.of(
                "messages", messageMapper.countUnreadAll(me),
                "notifications", notificationMapper.countUnread(me));
    }

    /** 位置消息必须携带合法经纬度与地点名称，避免写入无效坐标。 */
    private void validateLocation(String payloadJson) {
        JsonNode node = readPayload(payloadJson, "位置数据不合法");
        if (!node.isObject()) {
            throw ApiException.badRequest("位置数据不合法");
        }
        double lat = node.path("lat").asDouble(Double.NaN);
        double lng = node.path("lng").asDouble(Double.NaN);
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw ApiException.badRequest("位置坐标不合法");
        }
        String name = node.path("name").asText("").trim();
        if (name.isEmpty() || name.length() > 60) {
            throw ApiException.badRequest("位置名称不合法");
        }
    }

    /** 运动卡片必须包含运动类型、时长与消耗，避免客户端显示空卡片。 */
    private void validateRecord(String payloadJson) {
        JsonNode node = readPayload(payloadJson, "运动数据不合法");
        if (!node.isObject()) {
            throw ApiException.badRequest("运动数据不合法");
        }
        String sportType = node.path("type").asText("").trim();
        if (sportType.isEmpty() || sportType.length() > 30) {
            throw ApiException.badRequest("运动数据不合法");
        }
        if (node.path("date").asText("").length() > 20) {
            throw ApiException.badRequest("运动数据不合法");
        }
        JsonNode duration = node.get("duration");
        JsonNode calories = node.get("calories");
        if (duration == null || !duration.isNumber()
                || calories == null || !calories.isNumber()) {
            throw ApiException.badRequest("运动数据不合法");
        }
        int minutes = duration.asInt();
        int kcal = calories.asInt();
        if (minutes < 0 || minutes > 24 * 60 || kcal < 0 || kcal > 100000) {
            throw ApiException.badRequest("运动数据不合法");
        }
    }

    private JsonNode readPayload(String payloadJson, String message) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException error) {
            throw ApiException.badRequest(message);
        }
    }

    private boolean isMutual(String a, String b) {
        Long forward = followMapper.selectCount(
                Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getFollowerId, a)
                        .eq(Follow::getFolloweeId, b));
        if (forward == null || forward == 0) {
            return false;
        }
        Long back = followMapper.selectCount(
                Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getFollowerId, b)
                        .eq(Follow::getFolloweeId, a));
        return back != null && back > 0;
    }
}
