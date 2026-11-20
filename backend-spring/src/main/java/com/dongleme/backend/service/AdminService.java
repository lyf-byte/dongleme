package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.AdminNewUserRow;
import com.dongleme.backend.dto.AdminOverviewView;
import com.dongleme.backend.dto.AdminRankItem;
import com.dongleme.backend.dto.AdminRankView;
import com.dongleme.backend.dto.AdminRecentRow;
import com.dongleme.backend.dto.AdminRecentView;
import com.dongleme.backend.dto.AdminRecordRow;
import com.dongleme.backend.dto.AdminRecordView;
import com.dongleme.backend.dto.AdminRecordsView;
import com.dongleme.backend.dto.AdminRouteListView;
import com.dongleme.backend.dto.AdminRouteRow;
import com.dongleme.backend.dto.AdminRouteView;
import com.dongleme.backend.dto.AdminSummaryRow;
import com.dongleme.backend.dto.AdminTrendRow;
import com.dongleme.backend.dto.AdminTypeStatRow;
import com.dongleme.backend.dto.AdminUserDetailView;
import com.dongleme.backend.dto.AdminUserRow;
import com.dongleme.backend.dto.AdminUserStatsRow;
import com.dongleme.backend.dto.AdminUsersView;
import com.dongleme.backend.dto.AdminUserView;
import com.dongleme.backend.dto.RankRow;
import com.dongleme.backend.entity.ActivityRoute;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.ActivityRouteMapper;
import com.dongleme.backend.mapper.AdminStatsMapper;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.UserLookupCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Set<String> FILTERS =
            Set.of("all", "activeToday", "hasRecords", "noRecords", "admin");
    private static final Set<String> SORTS =
            Set.of("createdAt", "streak", "records", "weekMinutes", "lastCheck");

    /**
     * 后台查询专用线程池：数据库在远端，多条查询并行发出可把多次网络往返压缩成一次。
     */
    private static final ExecutorService QUERY_POOL = Executors.newFixedThreadPool(8, task -> {
        Thread thread = new Thread(task, "admin-query");
        thread.setDaemon(true);
        return thread;
    });

    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;
    private final ActivityRouteMapper routeMapper;
    private final AdminStatsMapper adminStatsMapper;
    private final UserLookupCache userLookupCache;
    private final ObjectMapper objectMapper;

    public AdminService(
            UserMapper userMapper,
            CheckinRecordMapper recordMapper,
            ActivityRouteMapper routeMapper,
            AdminStatsMapper adminStatsMapper,
            UserLookupCache userLookupCache,
            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.routeMapper = routeMapper;
        this.adminStatsMapper = adminStatsMapper;
        this.userLookupCache = userLookupCache;
        this.objectMapper = objectMapper;
    }

    public AdminOverviewView overview() {
        LocalDate today = ShanghaiTime.today();
        LocalDate weekStart = ShanghaiTime.weekStart(today);
        LocalDate trendFrom = today.minusDays(13);

        CompletableFuture<AdminSummaryRow> summaryFuture =
                async(() -> adminStatsMapper.selectSummary(today, weekStart));
        CompletableFuture<List<AdminTrendRow>> trendFuture =
                async(() -> recordMapper.selectTrend(trendFrom));
        CompletableFuture<List<AdminNewUserRow>> newUserFuture =
                async(() -> userMapper.selectNewUserTrend(trendFrom));
        CompletableFuture<List<AdminTypeStatRow>> typeFuture =
                async(recordMapper::selectTypeStats);
        CompletableFuture<List<AdminRecentRow>> recentFuture =
                async(recordMapper::selectRecentRecords);

        AdminSummaryRow summary = summaryFuture.join();
        Map<LocalDate, AdminTrendRow> recordMap = trendFuture.join().stream()
                .filter(row -> row.getDate() != null)
                .collect(Collectors.toMap(AdminTrendRow::getDate, row -> row, (a, b) -> a));
        Map<LocalDate, Long> newUserMap = newUserFuture.join().stream()
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

        List<AdminOverviewView.TypeStat> types = typeFuture.join().stream()
                .map(row -> new AdminOverviewView.TypeStat(
                        row.getName(),
                        row.getCount() == null ? 0 : row.getCount(),
                        row.getMinutes() == null ? 0 : row.getMinutes()))
                .toList();

        List<AdminRecentView> recent = recentFuture.join().stream()
                .map(this::toRecentView)
                .toList();

        return new AdminOverviewView(
                value(summary == null ? null : summary.getTotalUsers()),
                value(summary == null ? null : summary.getTotalRecords()),
                value(summary == null ? null : summary.getActiveToday()),
                value(summary == null ? null : summary.getWeekMinutes()),
                value(summary == null ? null : summary.getTodayRecords()),
                value(summary == null ? null : summary.getWeekNewUsers()),
                value(summary == null ? null : summary.getRouteCount()),
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
        int offset = (safePage - 1) * safeSize;
        List<AdminUserRow> rows = userMapper
                .selectAdminUsersPage(query, filter, sort, today, weekStart, safeSize, offset);

        long total;
        if (!rows.isEmpty()) {
            total = rows.get(0).getTotal() == null ? rows.size() : rows.get(0).getTotal();
        } else if (safePage == 1) {
            total = 0;
        } else {
            // 最后一页被删空时窗口函数没有行可返回，退回单独计数。
            total = userMapper.countAdminUsers(query, filter, today);
        }

        List<AdminUserView> users = rows.stream()
                .map(this::toUserView)
                .toList();

        return new AdminUsersView(users, total, safePage, safeSize);
    }

    public AdminUserDetailView userDetail(String id) {
        LocalDate weekStart = ShanghaiTime.weekStart(ShanghaiTime.today());

        CompletableFuture<User> userFuture = async(() -> userMapper.selectById(id));
        CompletableFuture<AdminUserStatsRow> statsFuture =
                async(() -> adminStatsMapper.selectUserStats(id, weekStart));
        CompletableFuture<List<CheckinRecord>> recordFuture = async(() -> recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, id)
                        .orderByDesc(CheckinRecord::getDate)
                        .orderByDesc(CheckinRecord::getCreatedAt)
                        .last("limit 10")));
        CompletableFuture<List<ActivityRoute>> routeFuture = async(() -> routeMapper.selectList(
                Wrappers.<ActivityRoute>lambdaQuery()
                        .eq(ActivityRoute::getUserId, id)
                        .orderByDesc(ActivityRoute::getCreatedAt)
                        .last("limit 5")));

        User user = userFuture.join();
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }

        AdminUserStatsRow stats = statsFuture.join();
        long recordCount = stats == null ? 0 : value(stats.getRecordCount());
        long weekMinutes = stats == null ? 0 : value(stats.getWeekMinutes());
        long routeCount = stats == null ? 0 : value(stats.getRouteCount());

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

        List<AdminRecordView> recordViews = recordFuture.join().stream()
                .map(row -> new AdminRecordView(
                        row.getId(),
                        row.getDate(),
                        row.getType(),
                        row.getDuration() == null ? 0 : row.getDuration(),
                        row.getCalories() == null ? 0 : row.getCalories(),
                        row.getUserId(),
                        user.getNickname()))
                .toList();

        List<AdminRouteView> routeViews = routeFuture.join().stream()
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
        String query = queryParam == null ? "" : queryParam.trim();
        int offset = (safePage - 1) * safeSize;

        List<AdminRecordRow> rows = adminStatsMapper
                .selectRecords(query, type, from, to, safeSize, offset);

        long total;
        if (!rows.isEmpty()) {
            total = rows.get(0).getTotal() == null ? rows.size() : rows.get(0).getTotal();
        } else if (safePage == 1) {
            total = 0;
        } else {
            total = adminStatsMapper.countRecords(query, type, from, to);
        }

        List<AdminRecordView> records = rows.stream()
                .map(row -> new AdminRecordView(
                        row.getId(),
                        row.getDate(),
                        row.getType(),
                        row.getDuration() == null ? 0 : row.getDuration(),
                        row.getCalories() == null ? 0 : row.getCalories(),
                        row.getUserId(),
                        row.getNickname() == null ? "运动新人" : row.getNickname()))
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
        String query = queryParam == null ? "" : queryParam.trim();
        int offset = (safePage - 1) * safeSize;

        List<AdminRouteRow> rows = adminStatsMapper
                .selectRoutes(query, type, from, to, safeSize, offset);

        long total;
        if (!rows.isEmpty()) {
            total = rows.get(0).getTotal() == null ? rows.size() : rows.get(0).getTotal();
        } else if (safePage == 1) {
            total = 0;
        } else {
            total = adminStatsMapper.countRoutes(query, type, from, to);
        }

        List<AdminRouteView> views = rows.stream()
                .map(row -> toRouteView(
                        row,
                        row.getNickname() == null ? "运动新人" : row.getNickname()))
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
            userLookupCache.invalidate(id);
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
            userLookupCache.invalidate(userId);
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
        userLookupCache.invalidate(userId);
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

    private AdminRecentView toRecentView(AdminRecentRow row) {
        return new AdminRecentView(
                row.getId(),
                row.getDate(),
                row.getType(),
                row.getDuration() == null ? 0 : row.getDuration(),
                row.getCalories() == null ? 0 : row.getCalories(),
                row.getNickname());
    }

    private AdminRouteView toRouteView(AdminRouteRow row, String nickname) {
        return new AdminRouteView(
                row.getId() == null ? 0 : row.getId(),
                row.getUserId(),
                nickname,
                row.getType(),
                row.getDate(),
                row.getDistanceM() == null ? 0 : row.getDistanceM(),
                row.getDurationSec() == null ? 0 : row.getDurationSec(),
                row.getCalories() == null ? 0 : row.getCalories(),
                row.getPointCount() == null ? 0 : row.getPointCount(),
                row.getCreatedAt(),
                parsePreview(row.getPreview()));
    }

    private AdminRouteView toRouteView(ActivityRoute route, String nickname) {
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
                parsePreview(route.getPreview()));
    }

    private List<double[]> parsePreview(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<double[]>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
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

    private static long value(Long number) {
        return number == null ? 0 : number;
    }

    private static <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, QUERY_POOL);
    }
}
