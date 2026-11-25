package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.StatsDaily;
import com.dongleme.backend.dto.StatsTotals;
import com.dongleme.backend.dto.StatsView;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.RecordMetrics;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StatsService {

    private static final Set<String> RANGES = Set.of("7d", "30d", "month");

    private final CheckinRecordMapper recordMapper;

    public StatsService(CheckinRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    public StatsView getStats(String userId, String rangeParam) {
        String range = rangeParam == null || rangeParam.isBlank() ? "7d" : rangeParam;
        if (!RANGES.contains(range)) {
            throw ApiException.badRequest("range 参数不合法");
        }

        LocalDate today = ShanghaiTime.today();
        LocalDate start = switch (range) {
            case "30d" -> today.minusDays(29);
            case "month" -> today.withDayOfMonth(1);
            default -> today.minusDays(6);
        };

        List<CheckinRecord> rows = recordMapper.selectList(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .ge(CheckinRecord::getDate, start)
                        .le(CheckinRecord::getDate, today));

        Map<LocalDate, DayAggregate> dayMap = new HashMap<>();
        Map<String, Integer> typeMap = new LinkedHashMap<>();
        int minutesTotal = 0;
        int caloriesTotal = 0;
        int recordCount = 0;

        for (CheckinRecord row : rows) {
            if (row.getFlagged() != null && row.getFlagged() == 1) {
                continue;
            }
            recordCount += 1;
            DayAggregate day = dayMap.computeIfAbsent(row.getDate(), key -> new DayAggregate());
            int duration = row.getDuration() == null ? 0 : row.getDuration();
            int calories = row.getCalories() == null ? 0 : row.getCalories();
            if (row.getVerified() != null && row.getVerified() == 1) {
                day.routeMinutes += duration;
                day.routeCalories += calories;
                day.routeTypes.merge(row.getType(), duration, Integer::sum);
            } else {
                day.manualMinutes += duration;
                day.manualCalories += calories;
                day.manualTypes.merge(row.getType(), duration, Integer::sum);
            }
        }

        List<StatsDaily> daily = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            DayAggregate day = dayMap.get(date);
            if (day == null) {
                daily.add(new StatsDaily(date, 0, 0, Map.of()));
                continue;
            }
            int minutes = day.effectiveMinutes();
            int calories = day.effectiveCalories();
            Map<String, Integer> types = day.effectiveTypes();
            minutesTotal += minutes;
            caloriesTotal += calories;
            types.forEach((type, value) -> {
                if (value > 0) {
                    typeMap.merge(type, value, Integer::sum);
                }
            });
            daily.add(new StatsDaily(date, minutes, calories, types));
        }

        return new StatsView(
                range,
                start,
                today,
                daily,
                typeMap,
                new StatsTotals(recordCount, minutesTotal, caloriesTotal)
        );
    }

    private static class DayAggregate {
        private int routeMinutes;
        private int manualMinutes;
        private int routeCalories;
        private int manualCalories;
        private final Map<String, Integer> routeTypes = new LinkedHashMap<>();
        private final Map<String, Integer> manualTypes = new LinkedHashMap<>();

        private int effectiveMinutes() {
            return routeMinutes
                    + Math.min(RecordMetrics.DAILY_MANUAL_CAP, manualMinutes);
        }

        private int cappedManualCalories() {
            if (manualMinutes <= 0) {
                return 0;
            }
            int capped = Math.min(RecordMetrics.DAILY_MANUAL_CAP, manualMinutes);
            if (capped >= manualMinutes) {
                return manualCalories;
            }
            return (int) Math.round(manualCalories * (capped / (double) manualMinutes));
        }

        private int effectiveCalories() {
            return routeCalories + cappedManualCalories();
        }

        private Map<String, Integer> effectiveTypes() {
            Map<String, Integer> merged = new LinkedHashMap<>(routeTypes);
            if (!manualTypes.isEmpty()) {
                int capped = Math.min(RecordMetrics.DAILY_MANUAL_CAP, manualMinutes);
                double ratio = manualMinutes <= 0 ? 0 : capped / (double) manualMinutes;
                manualTypes.forEach((type, value) ->
                        merged.merge(type, (int) Math.round(value * ratio), Integer::sum));
            }
            return merged;
        }
    }
}
