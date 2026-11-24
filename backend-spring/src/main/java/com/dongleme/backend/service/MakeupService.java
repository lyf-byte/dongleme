package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.MakeupRequest;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.MakeupCard;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.MakeupCardMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MakeupService {

    /** 每个自然月可用的补签卡数量 */
    private static final int MONTHLY_LIMIT = 2;
    /** 只能补最近 N 天（不含今天） */
    private static final int MAX_LOOKBACK_DAYS = 7;

    private final MakeupCardMapper makeupCardMapper;
    private final CheckinRecordMapper recordMapper;
    private final StreakService streakService;

    public MakeupService(
            MakeupCardMapper makeupCardMapper,
            CheckinRecordMapper recordMapper,
            StreakService streakService) {
        this.makeupCardMapper = makeupCardMapper;
        this.recordMapper = recordMapper;
        this.streakService = streakService;
    }

    public Map<String, Object> status(String userId) {
        LocalDate today = ShanghaiTime.today();
        int used = makeupCardMapper.countUsedSince(userId, today.withDayOfMonth(1).atStartOfDay());
        List<LocalDate> dates = makeupCardMapper.selectList(
                        Wrappers.<MakeupCard>lambdaQuery()
                                .eq(MakeupCard::getUserId, userId)
                                .orderByDesc(MakeupCard::getTargetDate))
                .stream()
                .map(MakeupCard::getTargetDate)
                .filter(Objects::nonNull)
                .toList();
        List<LocalDate> checked = recordMapper.selectList(
                        Wrappers.<CheckinRecord>lambdaQuery()
                                .eq(CheckinRecord::getUserId, userId)
                                .ge(CheckinRecord::getDate, today.minusDays(MAX_LOOKBACK_DAYS)))
                .stream()
                .map(CheckinRecord::getDate)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("limit", MONTHLY_LIMIT);
        result.put("used", used);
        result.put("remaining", Math.max(0, MONTHLY_LIMIT - used));
        result.put("window", MAX_LOOKBACK_DAYS);
        result.put("dates", dates);
        result.put("checked", checked);
        return result;
    }

    @Transactional
    public Map<String, Object> use(String userId, MakeupRequest request) {
        LocalDate today = ShanghaiTime.today();
        LocalDate target = parseDate(request == null ? null : request.date());
        if (!target.isBefore(today)) {
            throw ApiException.badRequest("只能补签今天之前的日期");
        }
        if (target.isBefore(today.minusDays(MAX_LOOKBACK_DAYS))) {
            throw ApiException.badRequest("只能补签最近 " + MAX_LOOKBACK_DAYS + " 天");
        }

        int used = makeupCardMapper.countUsedSince(userId, today.withDayOfMonth(1).atStartOfDay());
        if (used >= MONTHLY_LIMIT) {
            throw ApiException.conflict("本月补签卡已用完");
        }

        Long records = recordMapper.selectCount(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .eq(CheckinRecord::getDate, target));
        if (records != null && records > 0) {
            throw ApiException.badRequest("该日期已有打卡记录，无需补签");
        }

        MakeupCard card = new MakeupCard();
        card.setUserId(userId);
        card.setTargetDate(target);
        try {
            makeupCardMapper.insert(card);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("该日期已经补签过了");
        }

        int streak = streakService.recalculate(userId);
        Map<String, Object> result = new LinkedHashMap<>(status(userId));
        result.put("streak", streak);
        result.put("date", target);
        return result;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("请选择补签日期");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("日期格式不正确");
        }
    }
}
