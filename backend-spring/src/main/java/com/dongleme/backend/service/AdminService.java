package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.AdminOverviewView;
import com.dongleme.backend.dto.AdminRankItem;
import com.dongleme.backend.dto.AdminRankView;
import com.dongleme.backend.dto.AdminRecordView;
import com.dongleme.backend.dto.AdminRecordsView;
import com.dongleme.backend.dto.AdminRecentRow;
import com.dongleme.backend.dto.AdminRecentView;
import com.dongleme.backend.dto.AdminUserRow;
import com.dongleme.backend.dto.AdminUsersView;
import com.dongleme.backend.dto.AdminUserView;
import com.dongleme.backend.dto.RankRow;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;

    public AdminService(UserMapper userMapper, CheckinRecordMapper recordMapper) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
    }

    public AdminOverviewView overview() {
        LocalDate today = ShanghaiTime.today();
        LocalDate weekStart = ShanghaiTime.weekStart(today);

        long totalUsers = userMapper.selectCount(null);
        long totalRecords = recordMapper.selectCount(null);
        long activeToday = recordMapper.countActiveUsers(today);
        long weekMinutes = recordMapper.sumMinutesSince(weekStart);
        List<AdminRecentView> recent = recordMapper.selectRecentRecords().stream()
                .map(this::toRecentView)
                .toList();

        return new AdminOverviewView(
                totalUsers, totalRecords, activeToday, weekMinutes, recent);
    }

    public AdminUsersView users(String queryParam) {
        String query = queryParam == null ? "" : queryParam.trim().toLowerCase(Locale.ROOT);

        List<AdminUserView> users = userMapper.selectAdminUsers().stream()
                .map(this::toUserView)
                .filter(user -> query.isEmpty()
                        || user.nickname().toLowerCase(Locale.ROOT).contains(query)
                        || user.account().toLowerCase(Locale.ROOT).contains(query))
                .toList();

        return new AdminUsersView(users);
    }

    public AdminRecordsView records(String type, LocalDate from, LocalDate to) {
        LambdaQueryWrapper<CheckinRecord> wrapper = Wrappers.lambdaQuery();
        if (type != null && !type.isBlank()) {
            wrapper.eq(CheckinRecord::getType, type);
        }
        if (from != null) {
            wrapper.ge(CheckinRecord::getDate, from);
        }
        if (to != null) {
            wrapper.le(CheckinRecord::getDate, to);
        }
        wrapper.orderByDesc(CheckinRecord::getDate)
                .orderByDesc(CheckinRecord::getCreatedAt)
                .last("limit 200");

        List<CheckinRecord> rows = recordMapper.selectList(wrapper);
        Map<String, String> nicknames = loadNicknames(rows);

        List<AdminRecordView> records = rows.stream()
                .map(row -> new AdminRecordView(
                        row.getId(),
                        row.getDate(),
                        row.getType(),
                        row.getDuration() == null ? 0 : row.getDuration(),
                        row.getCalories() == null ? 0 : row.getCalories(),
                        row.getUserId(),
                        nicknames.getOrDefault(row.getUserId(), "运动新人")))
                .toList();

        return new AdminRecordsView(records);
    }

    public AdminRankView rank() {
        LocalDate weekStart = ShanghaiTime.weekStart(ShanghaiTime.today());
        List<RankRow> rows = userMapper.selectWeeklyRank(weekStart);

        List<AdminRankItem> list = rows.stream()
                .limit(100)
                .map(row -> new AdminRankItem(
                        row.getId(),
                        row.getNickname(),
                        row.getWeeklyMinutes() == null ? 0 : row.getWeeklyMinutes()))
                .toList();

        return new AdminRankView(list);
    }

    public Map<String, Object> delete(String action, String id, String currentUserId) {
        if (id == null || id.isBlank()) {
            throw ApiException.badRequest("缺少 id");
        }

        if ("user".equals(action)) {
            if (id.equals(currentUserId)) {
                throw ApiException.badRequest("不能删除当前管理员账号");
            }
            int affected = userMapper.deleteById(id);
            if (affected == 0) {
                throw ApiException.notFound("用户不存在");
            }
            return Map.of("ok", true);
        }

        if ("record".equals(action)) {
            long recordId;
            try {
                recordId = Long.parseLong(id);
            } catch (NumberFormatException exception) {
                throw ApiException.notFound("记录不存在");
            }
            int affected = recordMapper.deleteById(recordId);
            if (affected == 0) {
                throw ApiException.notFound("记录不存在");
            }
            return Map.of("ok", true);
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "未知的后台操作");
    }

    private AdminUserView toUserView(AdminUserRow row) {
        String account = row.getUsername() != null
                ? row.getUsername()
                : row.getEmail() != null
                        ? row.getEmail()
                        : row.getPhone() != null ? row.getPhone() : "—";

        return new AdminUserView(
                row.getId(),
                row.getNickname(),
                row.getWeight() == null ? 60 : row.getWeight().doubleValue(),
                row.getWeeklyGoal(),
                row.getStreak() == null ? 0 : row.getStreak(),
                Boolean.TRUE.equals(row.getAdmin()),
                row.getCreatedAt(),
                account,
                row.getRecordCount() == null ? 0 : row.getRecordCount().intValue()
        );
    }

    private AdminRecentView toRecentView(AdminRecentRow row) {
        return new AdminRecentView(
                row.getId(),
                row.getDate(),
                row.getType(),
                row.getDuration() == null ? 0 : row.getDuration(),
                row.getCalories() == null ? 0 : row.getCalories(),
                row.getNickname()
        );
    }

    private Map<String, String> loadNicknames(List<CheckinRecord> rows) {
        Set<String> userIds = rows.stream()
                .map(CheckinRecord::getUserId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> nicknames = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            nicknames.put(user.getId(), user.getNickname());
        }
        return nicknames;
    }
}
