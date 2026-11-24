package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.CheckinRequest;
import com.dongleme.backend.dto.CheckinResult;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.CustomSport;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.CustomSportMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.UserLookupCache;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class CheckinService {

    private static final int MANUAL_DAILY_LIMIT = 180;

    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;
    private final CustomSportMapper customSportMapper;
    private final UserLookupCache userLookupCache;

    public CheckinService(
            UserMapper userMapper,
            CheckinRecordMapper recordMapper,
            CustomSportMapper customSportMapper,
            UserLookupCache userLookupCache) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.customSportMapper = customSportMapper;
        this.userLookupCache = userLookupCache;
    }

    @Transactional
    public CheckinResult checkin(String userId, CheckinRequest request) {
        Double durationValue = request.duration();
        if (durationValue == null
                || !Double.isFinite(durationValue)
                || durationValue != Math.rint(durationValue)
                || durationValue < 1
                || durationValue > 600) {
            throw ApiException.badRequest("时长必须是 1–600 的整数");
        }
        int duration = durationValue.intValue();

        CheckinOutcome outcome = checkinAt(
                userId,
                request.type(),
                duration,
                request.weight(),
                Boolean.TRUE.equals(request.overwrite()),
                ShanghaiTime.today(),
                "manual",
                false);
        CheckinRecord record = outcome.record();
        return new CheckinResult(
                record.getDate(),
                record.getDuration(),
                record.getCalories(),
                outcome.streak());
    }

    /**
     * 打卡核心逻辑：运动类型/MET、体重、性别系数、ACSM 热量、记录写入和连续天数。
     * 轨迹保存也调用这里，避免重复实现。
     */
    @Transactional
    public CheckinOutcome checkinAt(
            String userId,
            String type,
            int duration,
            Double weightOverride,
            boolean overwrite,
            LocalDate date,
            String source,
            boolean verified) {
        String normalizedType = type == null ? "" : type;
        Double met = Sports.BUILTIN_MET.get(normalizedType);
        if (met == null) {
            CustomSport customSport = customSportMapper.selectOne(
                    Wrappers.<CustomSport>lambdaQuery()
                            .eq(CustomSport::getUserId, userId)
                            .eq(CustomSport::getName, normalizedType)
                            .last("limit 1"));
            if (customSport == null) {
                throw ApiException.badRequest("运动类型不在可选范围内");
            }
            met = customSport.getMet().doubleValue();
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }

        double weight = weightOverride != null
                ? weightOverride
                : user.getWeight() != null ? user.getWeight().doubleValue() : 60;
        if (!Double.isFinite(weight) || weight < 20 || weight > 300) {
            throw ApiException.badRequest("体重需在 20–300 kg 之间");
        }

        LocalDate checkinDate = date == null ? ShanghaiTime.today() : date;
        double factor = Sports.genderFactor(user.getGender());
        // ACSM 估算：MET × 3.5 × 体重(kg) ÷ 200 每分钟
        int calories = (int) Math.round(met * 3.5 * weight * duration / 200.0 * factor);

        CheckinRecord record = new CheckinRecord();
        record.setUserId(userId);
        record.setDate(checkinDate);
        record.setType(normalizedType);
        record.setDuration(duration);
        record.setCalories(calories);
        record.setSource(source == null ? "manual" : source);
        record.setVerified(verified ? 1 : 0);
        int flagged = 0;
        if (!verified) {
            int otherManual = recordMapper.selectList(
                    Wrappers.<CheckinRecord>lambdaQuery()
                            .eq(CheckinRecord::getUserId, userId)
                            .eq(CheckinRecord::getDate, checkinDate)
                            .eq(CheckinRecord::getVerified, 0)
                            .ne(CheckinRecord::getType, normalizedType))
                    .stream()
                    .mapToInt(r -> r.getDuration() == null ? 0 : r.getDuration())
                    .sum();
            if (otherManual + duration > MANUAL_DAILY_LIMIT) {
                flagged = 1;
            }
        }
        record.setFlagged(flagged);

        try {
            if (overwrite) {
                recordMapper.upsert(record);
            } else {
                recordMapper.insert(record);
            }
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("今日已打卡该类型");
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存打卡记录失败");
        }

        int currentStreak = user.getStreak() == null ? 0 : user.getStreak();
        LocalDate lastCheckDate = user.getLastCheckDate();
        int streak = 1;
        if (checkinDate.equals(lastCheckDate)) {
            streak = currentStreak > 0 ? currentStreak : 1;
        } else if (lastCheckDate != null
                && ChronoUnit.DAYS.between(lastCheckDate, checkinDate) == 1) {
            streak = currentStreak + 1;
        }

        try {
            User patch = new User();
            patch.setId(userId);
            patch.setStreak(streak);
            patch.setLastCheckDate(checkinDate);
            userMapper.updateById(patch);
            userLookupCache.invalidate(userId);
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存打卡记录失败");
        }

        return new CheckinOutcome(record, streak);
    }

    public record CheckinOutcome(CheckinRecord record, int streak) {
    }
}
