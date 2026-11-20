package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.AdminNewUserRow;
import com.dongleme.backend.dto.AdminOverviewView;
import com.dongleme.backend.dto.AdminRankItem;
import com.dongleme.backend.dto.AdminRankView;
import com.dongleme.backend.dto.AdminRecentView;
import com.dongleme.backend.dto.AdminRecordView;
import com.dongleme.backend.dto.AdminRecordsView;
import com.dongleme.backend.dto.AdminRouteListView;
import com.dongleme.backend.dto.AdminRouteView;
import com.dongleme.backend.dto.AdminTrendRow;
import com.dongleme.backend.dto.AdminUserDetailView;
import com.dongleme.backend.dto.AdminUserRow;
import com.dongleme.backend.dto.AdminUsersView;
import com.dongleme.backend.dto.AdminUserView;
import com.dongleme.backend.dto.RankRow;
import com.dongleme.backend.entity.ActivityRoute;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.ActivityRouteMapper;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Set<String> FILTERS =
            Set.of("all", "activeToday", "hasRecords", "noRecords", "admin");
    private static final Set<String> SORTS =
            Set.of("createdAt", "streak", "records", "weekMinutes", "lastCheck");

    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;
    private final ActivityRouteMapper routeMapper;
    private final ObjectMapper objectMapper;

    public AdminService(
            UserMapper userMapper,
            CheckinRecordMapper recordMapper,
            ActivityRouteMapper routeMapper,
            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.routeMapper = routeMapper;
        this.objectMapper = objectMapper;
    }

    public AdminOverviewView overview() {
        LocalDate today = ShanghaiTime.today();
        LocalDate weekStart = ShanghaiTime.weekStart(today);
        LocalDate trendFrom = today.minusDays(13);

        long totalUsers = userMapper.selectCount(null);
        long totalRecords = recordMapper.selectCount(null);
        long activeToday = recordMapper.countActiveUsers(today);
        long todayRecords = recordMapper.countByDate(today);
        long weekMinutes = recordMapper.sumMinutesSince(weekStart);
        long weekNewUsers = userMapper.countNewUsersSince(weekStart.atStartOfDay());
        long routeCount = routeMapper.selectCount(null);

        Map<LocalDate, AdminTrendRow> recordMap = recordMapper.selectTrend(trendFrom).stream()
                .filter(row -> row.getDate() != null)
                .collect(Collectors.toMap(AdminTrendRow::getDate, row -> row, (a, b) -> a));
        Map<LocalDate, Long> newUserMap = userMapper.selectNewUserTrend(trendFrom).stream()
                .filter(row -> row.getDate() != null)
                .collect(Collectors.toMap(
                        AdminNewUserRow::getDate,
                        row -> row.getNewUsers() == null ? 0 : row.getNewUsers(),
                        Long::sum));

        List<AdminOverviewView.TrendPoint> trend = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            LocalDate date = trendFrom.plusDays(index);
            AdminTrendRow row = recordMap.get(date);
            trend.add(new AdminOverviewView.TrendPoint(
                    date,
                    row == null || row.getRecords() == null ? 0 : row.getRecords(),
                    row == null || row.getUsers() == null ? 0 : row.getUsers(),
                    newUserMap.getOrDefault(date, 0L)));
        }

        List<AdminOverviewView.TypeStat> types = recordMapper.selectTypeStats().stream()
                .map(row -> new AdminOverviewView.TypeStat(
                        row.getName(),
                        row.getCount() == null ? 0 : row.getCount(),
                        row.getMinutes() == null ? 0 : row.getMinutes()))
                .toList();

        List<AdminRecentView> recent = recordMapper.selectRecentRecords().stream()
                .map(this::toRecentView)
                .toList();

        return new AdminOverviewView(
                totalUsers,
                totalRecords,
                activeToday,
                weekMinutes,
                todayRecords,
                weekNewUsers,
                routeCount,
                trend,
                types,
                recent);
    }

    public AdminUsersView users(
            String queryParam,
            String filterParam,
            String sortParam,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        String query = queryParam == null ? "" : queryParam.trim();
        String filter = filterParam != null && FILTERS.contains(filterParam) ? filterParam : "all";
        String sort = sortParam != null && SORTS.contains(sortParam) ? sortParam : "createdAt";

        LocalDate today = ShanghaiTime.today();
        LocalDate weekStart = ShanghaiTime.weekStart(today);
        long total = userMapper.countAdminUsers(query, filter, today);
        int offset = (safePage - 1) * safeSize;
        List<AdminUserView> users = userMapper
                .selectAdminUsersPage(query, filter, sort, today, weekStart, safeSize, offset)
                .stream()
                .map(this::toUserView)
                .toList();

        return new AdminUsersView(users, total, safePage, safeSize);
    }

    public AdminUserDetailView userDetail(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }

        LocalDate weekStart = ShanghaiTime.weekStart(ShanghaiTime.today());
        long recordCount = recordMapper.selectCount(
                Wrappers.<CheckinRecord>lambdaQuery().eq(CheckinRecord::getUserId, id));
        long weekMinutes = recordMapper.selectList(
                        Wrappers.<CheckinRecord>lambdaQuery()
                                .eq(CheckinRecord::getUserId, id)
                                .ge(CheckinRecord::getDate, weekStart))
                .stream()
                .mapToLong(row -> row.getDuration() == null ? 0 : row.getDuration())
                .sum();
        long routeCount = routeMapper.selectCount(
                Wrappers.<ActivityRoute>lambdaQuery().eq(ActivityRoute::getUserId, id));

        List<CheckinRecord> records = recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, id)
                        .orderByDesc(CheckinRecord::getDate)
                        .orderByDesc(CheckinRecord::getCreatedAt)
                        .last("limit 10"));
        List<ActivityRoute> routes = routeMapper.selectList(
                Wrappers.<ActivityRoute>lambdaQuery()
                        .eq(ActivityRoute::getUserId, id)
                        .orderByDesc(ActivityRoute::getCreatedAt)
                        .last("limit 5"));

        AdminUserView view = new AdminUserView(
                user.getId(),
                user.getNickname(),
                user.getGender(),
                user.getWeight() == null ? 60 : user.getWeight().doubleValue(),
                user.getWeeklyGoal(),
                user.getStreak() == null ? 0 : user.getStreak(),
                user.getLastCheckDate(),
                Boolean.TRUE.equals(user.getIsAdmin()),
                user.getCreatedAt(),
                accountOf(user),
                (int) recordCount,
                weekMinutes,
                routeCount);

        List<AdminRecordView> recordViews = records.stream()
                .map(row -> new AdminRecordView(
                        row.getId(),
                        row.getDate(),
                        row.getType(),
                        row.getDuration() == null ? 0 : row.getDuration(),
                        row.getCalories() == null ? 0 : row.getCalories(),
                        row.getUserId(),
                        user.getNickname()))
                .toList();

        List<AdminRouteView> routeViews = routes.stream()
                .map(row -> toRouteView(row, user.getNickname()))
                .toList();

        return new AdminUserDetailView(view, recordViews, routeViews);
    }

    public AdminRecordsView records(
            String queryParam,
            String type,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

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

        String query = queryParam == null ? "" : queryParam.trim();
        if (!query.isEmpty()) {
            List<String> userIds = userMapper.selectList(
                            Wrappers.<User>lambdaQuery()
                                    .select(User::getId)
                                    .like(User::getNickname, query))
                    .stream()
                    .map(User::getId)
                    .toList();
            if (userIds.isEmpty()) {
                return new AdminRecordsView(List.of(), 0, safePage, safeSize);
            }
            wrapper.in(CheckinRecord::getUserId, userIds);
        }

        long total = recordMapper.selectCount(wrapper);
        int offset = (safePage - 1) * safeSize;
        wrapper.orderByDesc(CheckinRecord::getDate)
                .orderByDesc(CheckinRecord::getCreatedAt)
                .last("limit " + safeSize + " offset " + offset);

        List<CheckinRecord> rows = recordMapper.selectList(wrapper);
        Map<String, String> nicknames = loadNicknames(
                rows.stream().map(CheckinRecord::getUserId).collect(Collectors.toSet()));

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

        return new AdminRecordsView(records, total, safePage, safeSize);
    }

    public AdminRouteListView routes(
            String queryParam,
            String type,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);

        LambdaQueryWrapper<ActivityRoute> wrapper = Wrappers.lambdaQuery();
        if (type != null && !type.isBlank()) {
            wrapper.eq(ActivityRoute::getType, type);
        }
        if (from != null) {
            wrapper.ge(ActivityRoute::getDate, from);
        }
        if (to != null) {
            wrapper.le(ActivityRoute::getDate, to);
        }

        String query = queryParam == null ? "" : queryParam.trim();
        if (!query.isEmpty()) {
            List<String> userIds = userMapper.selectList(
                            Wrappers.<User>lambdaQuery()
                                    .select(User::getId)
                                    .like(User::getNickname, query))
                    .stream()
                    .map(User::getId)
                    .toList();
            if (userIds.isEmpty()) {
                return new AdminRouteListView(List.of(), 0, safePage, safeSize);
            }
            wrapper.in(ActivityRoute::getUserId, userIds);
        }

        long total = routeMapper.selectCount(wrapper);
        int offset = (safePage - 1) * safeSize;
        wrapper.orderByDesc(ActivityRoute::getCreatedAt)
                .last("limit " + safeSize + " offset " + offset);

        List<ActivityRoute> rows = routeMapper.selectList(wrapper);
        Map<String, String> nicknames = loadNicknames(
                rows.stream().map(ActivityRoute::getUserId).collect(Collectors.toSet()));

        List<AdminRouteView> views = rows.stream()
                .map(row -> toRouteView(
                        row,
                        nicknames.getOrDefault(row.getUserId(), "运动新人")))
                .toList();

        return new AdminRouteListView(views, total, safePage, safeSize);
    }

    public AdminRankView rank(String scope) {
        boolean total = "total".equals(scope);
        List<RankRow> rows = total
                ? userMapper.selectTotalRank()
                : userMapper.selectWeeklyRank(ShanghaiTime.weekStart(ShanghaiTime.today()));

        List<AdminRankItem> list = rows.stream()
                .limit(100)
                .map(row -> new AdminRankItem(
                        row.getId(),
                        row.getNickname(),
                        row.getWeeklyMinutes() == null ? 0 : row.getWeeklyMinutes()))
                .toList();

        return new AdminRankView(list);
    }

    public List<String> types() {
        return recordMapper.selectObjs(
                        Wrappers.<CheckinRecord>query()
                                .select("distinct type")
                                .orderByAsc("type"))
                .stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(type -> !type.isBlank())
                .toList();
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
            CheckinRecord record = recordMapper.selectById(parseId(id));
            if (record == null) {
                throw ApiException.notFound("记录不存在");
            }
            recordMapper.deleteById(record.getId());
            recalculateStreak(record.getUserId());
            return Map.of("ok", true);
        }

        if ("route".equals(action)) {
            ActivityRoute route = routeMapper.selectById(parseId(id));
            if (route == null) {
                throw ApiException.notFound("轨迹不存在");
            }
            routeMapper.deleteById(route.getId());
            return Map.of("ok", true);
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "未知的后台操作");
    }

    private long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw ApiException.notFound("数据不存在");
        }
    }

    private void recalculateStreak(String userId) {
        List<CheckinRecord> rows = recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId));

        Set<LocalDate> dates = rows.stream()
                .map(CheckinRecord::getDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (dates.isEmpty()) {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, userId)
                    .set(User::getStreak, 0)
                    .set(User::getLastCheckDate, null));
            return;
        }

        LocalDate latest = dates.stream().max(LocalDate::compareTo).orElseThrow();
        int streak = 1;
        for (LocalDate cursor = latest.minusDays(1); dates.contains(cursor); cursor = cursor.minusDays(1)) {
            streak++;
        }

        User patch = new User();
        patch.setId(userId);
        patch.setStreak(streak);
        patch.setLastCheckDate(latest);
        userMapper.updateById(patch);
    }

    private AdminUserView toUserView(AdminUserRow row) {
        return new AdminUserView(
                row.getId(),
                row.getNickname(),
                row.getGender(),
                row.getWeight() == null ? 60 : row.getWeight().doubleValue(),
                row.getWeeklyGoal(),
                row.getStreak() == null ? 0 : row.getStreak(),
                row.getLastCheckDate(),
                Boolean.TRUE.equals(row.getAdmin()),
                row.getCreatedAt(),
                accountOf(row),
                row.getRecordCount() == null ? 0 : row.getRecordCount().intValue(),
                row.getWeekMinutes() == null ? 0 : row.getWeekMinutes(),
                row.getRouteCount() == null ? 0 : row.getRouteCount());
    }

    private AdminRecentView toRecentView(com.dongleme.backend.dto.AdminRecentRow row) {
        return new AdminRecentView(
                row.getId(),
                row.getDate(),
                row.getType(),
                row.getDuration() == null ? 0 : row.getDuration(),
                row.getCalories() == null ? 0 : row.getCalories(),
                row.getNickname());
    }

    private AdminRouteView toRouteView(ActivityRoute route, String nickname) {
        List<double[]> preview = List.of();
        if (route.getPreview() != null && !route.getPreview().isBlank()) {
            try {
                preview = objectMapper.readValue(
                        route.getPreview(), new TypeReference<List<double[]>>() {
                        });
            } catch (Exception ignored) {
                preview = List.of();
            }
        }
        return new AdminRouteView(
                route.getId() == null ? 0 : route.getId(),
                route.getUserId(),
                nickname,
                route.getType(),
                route.getDate(),
                route.getDistanceM() == null ? 0 : route.getDistanceM(),
                route.getDurationSec() == null ? 0 : route.getDurationSec(),
                route.getCalories() == null ? 0 : route.getCalories(),
                route.getPointCount() == null ? 0 : route.getPointCount(),
                route.getCreatedAt(),
                preview);
    }

    private String accountOf(AdminUserRow row) {
        return accountOf(row.getUsername(), row.getEmail(), row.getPhone());
    }

    private String accountOf(User user) {
        return accountOf(user.getUsername(), user.getEmail(), user.getPhone());
    }

    private String accountOf(String username, String email, String phone) {
        if (username != null) {
            return username;
        }
        if (email != null) {
            return email;
        }
        return phone != null ? phone : "—";
    }

    private Map<String, String> loadNicknames(Collection<String> userIds) {
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
