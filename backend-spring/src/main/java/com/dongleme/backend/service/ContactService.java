package com.dongleme.backend.service;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.BlockRequest;
import com.dongleme.backend.dto.ConversationSettingRequest;
import com.dongleme.backend.dto.ConversationSettingRow;
import com.dongleme.backend.dto.MarkRemarkRequest;
import com.dongleme.backend.dto.ReportRequest;
import com.dongleme.backend.mapper.ContactRemarkMapper;
import com.dongleme.backend.mapper.ConversationSettingMapper;
import com.dongleme.backend.mapper.UserBlockMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.mapper.UserReportMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 联系人备注：只影响自己看到的称呼，不会改变对方昵称。
 */
@Service
public class ContactService {

    private static final int MAX_REMARK_LENGTH = 20;
    private static final int MAX_REPORT_LENGTH = 200;

    private final ContactRemarkMapper contactRemarkMapper;
    private final UserBlockMapper userBlockMapper;
    private final UserReportMapper userReportMapper;
    private final ConversationSettingMapper conversationSettingMapper;
    private final UserMapper userMapper;

    public ContactService(
            ContactRemarkMapper contactRemarkMapper,
            UserBlockMapper userBlockMapper,
            UserReportMapper userReportMapper,
            ConversationSettingMapper conversationSettingMapper,
            UserMapper userMapper) {
        this.contactRemarkMapper = contactRemarkMapper;
        this.userBlockMapper = userBlockMapper;
        this.userReportMapper = userReportMapper;
        this.conversationSettingMapper = conversationSettingMapper;
        this.userMapper = userMapper;
    }

    public Map<String, Object> setRemark(String me, MarkRemarkRequest request) {
        String targetId = request == null ? null : request.targetId();
        if (targetId == null || targetId.isBlank()) {
            throw ApiException.badRequest("缺少用户 id");
        }
        if (targetId.equals(me)) {
            throw ApiException.badRequest("不能给自己设置备注");
        }
        if (userMapper.selectById(targetId) == null) {
            throw ApiException.notFound("用户不存在");
        }

        String remark = request.remark() == null ? "" : request.remark().trim();
        if (remark.isEmpty()) {
            contactRemarkMapper.delete(me, targetId);
            return Map.of("ok", true, "remark", "");
        }
        if (remark.length() > MAX_REMARK_LENGTH) {
            throw ApiException.badRequest("备注最多 " + MAX_REMARK_LENGTH + " 个字");
        }
        contactRemarkMapper.upsert(me, targetId, remark);
        return Map.of("ok", true, "remark", remark);
    }

    public Map<String, Object> setBlocked(String me, BlockRequest request) {
        String targetId = request == null ? null : request.targetId();
        requireTarget(me, targetId);
        boolean blocked = request.blocked() == null || request.blocked();
        if (blocked) {
            userBlockMapper.block(me, targetId);
        } else {
            userBlockMapper.unblock(me, targetId);
        }
        return Map.of("ok", true, "blocked", blocked);
    }

    public Map<String, Object> report(String me, ReportRequest request) {
        String targetId = request == null ? null : request.targetId();
        requireTarget(me, targetId);
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > MAX_REPORT_LENGTH) {
            throw ApiException.badRequest("举报原因需为 1–" + MAX_REPORT_LENGTH + " 个字");
        }
        userReportMapper.insert(me, targetId, reason);
        return Map.of("ok", true);
    }

    public Map<String, Object> updateConversationSetting(String me, ConversationSettingRequest request) {
        String peerId = request == null ? null : request.peerId();
        requireTarget(me, peerId);
        ConversationSettingRow current = conversationSettingMapper.select(me, peerId);
        boolean pinned = request.pinned() != null
                ? request.pinned()
                : current != null && Boolean.TRUE.equals(current.getPinned());
        boolean muted = request.muted() != null
                ? request.muted()
                : current != null && Boolean.TRUE.equals(current.getMuted());
        conversationSettingMapper.upsert(me, peerId, pinned, muted);
        return Map.of("ok", true, "pinned", pinned, "muted", muted);
    }

    private void requireTarget(String me, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw ApiException.badRequest("缺少用户 id");
        }
        if (targetId.equals(me)) {
            throw ApiException.badRequest("不能对自己操作");
        }
        if (userMapper.selectById(targetId) == null) {
            throw ApiException.notFound("用户不存在");
        }
    }
}
