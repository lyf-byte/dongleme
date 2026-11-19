package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.StatsDaily;
import com.dongleme.backend.dto.StatsTotals;
import com.dongleme.backend.dto.StatsView;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.mapper.CheckinRecordMapper;
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

        for (CheckinRecord row : rows) {
            int duration = row.getDuration() == null ? 0 : row.getDuration();
            int calories = row.getCalories() == null ? 0 : row.getCalories();
            minutesTotal += duration;
            caloriesTotal += calories;
            typeMap.merge(row.getType(), duration, Integer::sum);

            DayAggregate day = dayMap.computeIfAbsent(row.getDate(), key -> new DayAggregate());
            day.minutes += duration;
            day.calories += calories;
            day.types.merge(row.getType(), duration, Integer::sum);
        }

        List<StatsDaily> daily = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            DayAggregate day = dayMap.get(date);
            if (day == null) {
                daily.add(new StatsDaily(date, 0, 0, Map.of()));
            } else {
                daily.add(new StatsDaily(date, day.minutes, day.calories, day.types));
            }
        }

        return new StatsView(
                range,
                start,
                today,
                daily,
                typeMap,
                new StatsTotals(rows.size(), minutesTotal, caloriesTotal)
        );
    }

    private static class DayAggregate {
        private int minutes;
        private int calories;
        private final Map<String, Integer> types = new LinkedHashMap<>();
    }
}
