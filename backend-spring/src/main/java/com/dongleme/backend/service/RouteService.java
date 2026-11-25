package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.GeoDistance;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.CheckinResult;
import com.dongleme.backend.dto.RouteCreateRequest;
import com.dongleme.backend.dto.RouteDetailView;
import com.dongleme.backend.dto.RoutePoint;
import com.dongleme.backend.dto.RouteSaveResult;
import com.dongleme.backend.dto.RouteSummaryView;
import com.dongleme.backend.entity.ActivityRoute;
import com.dongleme.backend.mapper.ActivityRouteMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RouteService {

    private static final Set<String> ALLOWED_TYPES = Set.of("跑步", "快走", "骑行");
    private static final int MAX_POINTS = 5000;
    private static final int MAX_ACCURACY_METERS = 50;
    private static final double MAX_SPEED_METERS_PER_SECOND = 20;
    private static final double MIN_POINT_GAP_METERS = 2;
    private static final int MIN_DISTANCE_METERS = 50;

    private final ActivityRouteMapper routeMapper;
    private final CheckinService checkinService;
    private final ObjectMapper objectMapper;

    public RouteService(
            ActivityRouteMapper routeMapper,
            CheckinService checkinService,
            ObjectMapper objectMapper) {
        this.routeMapper = routeMapper;
        this.checkinService = checkinService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RouteSaveResult create(String userId, RouteCreateRequest request) {
        String type = request.type() == null ? "" : request.type().trim();
        if (!ALLOWED_TYPES.contains(type)) {
            throw ApiException.badRequest("轨迹仅支持跑步、快走、骑行");
        }

        String clientId = request.clientId() == null ? "" : request.clientId().trim();
        if (!clientId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw ApiException.badRequest("缺少有效的 clientId");
        }

        ActivityRoute existing = routeMapper.selectByClientId(userId, clientId);
        if (existing != null) {
            return new RouteSaveResult(RouteSummaryView.of(existing, objectMapper), null);
        }

        if (request.startedAt() == null || request.endedAt() == null) {
            throw ApiException.badRequest("缺少开始或结束时间");
        }

        LocalDateTime started = LocalDateTime.ofInstant(
                request.startedAt().toInstant(), ShanghaiTime.ZONE);
        LocalDateTime ended = LocalDateTime.ofInstant(
                request.endedAt().toInstant(), ShanghaiTime.ZONE);
        if (!ended.isAfter(started)) {
            throw ApiException.badRequest("结束时间需晚于开始时间");
        }

        int durationSec = request.durationSec() == null ? 0 : request.durationSec();
        if (durationSec < 60 || durationSec > 21600) {
            throw ApiException.badRequest("轨迹时长需在 1–360 分钟");
        }
        long wallSeconds = Duration.between(started, ended).getSeconds();
        if (durationSec > wallSeconds + 10) {
            throw ApiException.badRequest("轨迹时长与起止时间不一致");
        }

        List<RoutePoint> accepted = filterPoints(request.points());
        if (accepted.size() < 2) {
            throw ApiException.badRequest("有效轨迹点太少");
        }

        double distance = 0;
        for (int i = 1; i < accepted.size(); i++) {
            RoutePoint previous = accepted.get(i - 1);
            RoutePoint current = accepted.get(i);
            distance += GeoDistance.meters(
                    previous.lat(), previous.lng(),
                    current.lat(), current.lng());
        }
        int distanceM = (int) Math.round(distance);
        if (distanceM < MIN_DISTANCE_METERS) {
            throw ApiException.badRequest("轨迹太短，至少 50 米");
        }
        if (distanceM > durationSec * MAX_SPEED_METERS_PER_SECOND + 500) {
            throw ApiException.badRequest("轨迹距离异常");
        }

        int minutes = Math.max(1, Math.min(600, (int) Math.round(durationSec / 60.0)));
        LocalDate date = ShanghaiTime.today();
        CheckinService.CheckinOutcome outcome = checkinService.checkinAt(
                userId,
                type,
                minutes,
                null,
                Boolean.TRUE.equals(request.overwrite()),
                date,
                "route",
                true);

        ActivityRoute route = new ActivityRoute();
        route.setUserId(userId);
        route.setDate(date);
        route.setType(type);
        route.setStartedAt(started);
        route.setEndedAt(ended);
        route.setDurationSec(durationSec);
        route.setDistanceM(distanceM);
        route.setCalories(outcome.record().getCalories());
        route.setPointCount(accepted.size());
        route.setStartLat(toDecimal(accepted.get(0).lat()));
        route.setStartLng(toDecimal(accepted.get(0).lng()));
        RoutePoint last = accepted.get(accepted.size() - 1);
        route.setEndLat(toDecimal(last.lat()));
        route.setEndLng(toDecimal(last.lng()));
        route.setClientId(clientId);
        route.setSource("android".equalsIgnoreCase(request.source()) ? "android" : "web");
        route.setCreatedAt(LocalDateTime.now(ShanghaiTime.ZONE));

        try {
            route.setPoints(objectMapper.writeValueAsString(accepted));
            route.setPreview(buildPreview(accepted));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存轨迹失败");
        }

        routeMapper.insert(route);
        CheckinResult checkin = new CheckinResult(
                date,
                minutes,
                outcome.record().getCalories(),
                outcome.streak(),
                outcome.record().getFlagged());
        return new RouteSaveResult(RouteSummaryView.of(route, objectMapper), checkin);
    }

    private String buildPreview(List<RoutePoint> points) throws JsonProcessingException {
        final int limit = 24;
        List<double[]> coords = new ArrayList<>();
        if (points.size() <= limit) {
            for (RoutePoint point : points) {
                coords.add(new double[]{point.lat(), point.lng()});
            }
        } else {
            for (int i = 0; i < limit; i++) {
                int index = (int) Math.round((double) i * (points.size() - 1) / (limit - 1));
                RoutePoint point = points.get(index);
                coords.add(new double[]{point.lat(), point.lng()});
            }
        }
        return objectMapper.writeValueAsString(coords);
    }

    public List<RouteSummaryView> list(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        List<ActivityRoute> rows = routeMapper.selectList(
                Wrappers.<ActivityRoute>lambdaQuery()
                        .select(
                                ActivityRoute::getId,
                                ActivityRoute::getDate,
                                ActivityRoute::getType,
                                ActivityRoute::getDistanceM,
                                ActivityRoute::getDurationSec,
                                ActivityRoute::getCalories,
                                ActivityRoute::getStartedAt,
                                ActivityRoute::getPointCount,
                                ActivityRoute::getPreview)
                        .eq(ActivityRoute::getUserId, userId)
                        .orderByDesc(ActivityRoute::getCreatedAt)
                        .last("limit " + safeLimit));
        return rows.stream().map(route -> RouteSummaryView.of(route, objectMapper)).toList();
    }

    public Map<String, Object> delete(String userId, long id) {
        ActivityRoute route = routeMapper.selectById(id);
        if (route == null || !userId.equals(route.getUserId())) {
            throw ApiException.notFound("轨迹不存在");
        }
        routeMapper.deleteById(id);
        return Map.of("ok", true);
    }

    public RouteDetailView detail(String userId, long id) {
        ActivityRoute route = routeMapper.selectById(id);
        if (route == null || !userId.equals(route.getUserId())) {
            throw ApiException.notFound("轨迹不存在");
        }

        List<RoutePoint> points;
        try {
            points = objectMapper.readValue(
                    route.getPoints(),
                    new TypeReference<List<RoutePoint>>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "轨迹数据异常");
        }

        return new RouteDetailView(
                route.getId(),
                route.getDate(),
                route.getType(),
                route.getDistanceM() == null ? 0 : route.getDistanceM(),
                route.getDurationSec() == null ? 0 : route.getDurationSec(),
                route.getCalories() == null ? 0 : route.getCalories(),
                route.getStartedAt(),
                route.getEndedAt(),
                route.getPointCount() == null ? 0 : route.getPointCount(),
                points);
    }

    private List<RoutePoint> filterPoints(List<RoutePoint> raw) {
        if (raw == null || raw.size() < 2 || raw.size() > MAX_POINTS) {
            throw ApiException.badRequest("轨迹点数量需在 2–5000 之间");
        }

        List<RoutePoint> accepted = new ArrayList<>();
        for (RoutePoint point : raw) {
            if (point == null || point.lat() == null || point.lng() == null || point.t() == null) {
                throw ApiException.badRequest("轨迹点数据不完整");
            }
            double lat = point.lat();
            double lng = point.lng();
            int t = point.t();
            double accuracy = point.acc() == null ? 0 : point.acc();
            if (!Double.isFinite(lat) || !Double.isFinite(lng) || !Double.isFinite(accuracy)
                    || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                throw ApiException.badRequest("轨迹点经纬度不合法");
            }
            if (t < 0 || t > 43200 || accuracy < 0 || accuracy > 2000) {
                throw ApiException.badRequest("轨迹点时间或精度不合法");
            }
            if (accuracy > MAX_ACCURACY_METERS) {
                continue;
            }

            if (!accepted.isEmpty()) {
                RoutePoint previous = accepted.get(accepted.size() - 1);
                double gap = GeoDistance.meters(
                        previous.lat(), previous.lng(), lat, lng);
                if (gap < MIN_POINT_GAP_METERS) {
                    continue;
                }
                int deltaTime = t - previous.t();
                if (deltaTime <= 0) {
                    throw ApiException.badRequest("轨迹点时间顺序错误");
                }
                if (gap / deltaTime > MAX_SPEED_METERS_PER_SECOND) {
                    continue;
                }
            }
            accepted.add(new RoutePoint(lat, lng, t, accuracy));
        }
        return accepted;
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }
}
