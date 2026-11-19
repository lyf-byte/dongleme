package com.dongleme.backend.service;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.CheckinRequest;
import com.dongleme.backend.dto.CheckinResult;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class CheckinService {

    private static final Map<String, Double> MET = Map.of(
            "跑步", 8.0,
            "快走", 4.3,
            "骑行", 7.5,
            "跳绳", 12.3,
            "游泳", 6.0,
            "力量训练", 5.5
    );

    private final UserMapper userMapper;
    private final CheckinRecordMapper recordMapper;

    public CheckinService(UserMapper userMapper, CheckinRecordMapper recordMapper) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
    }

    @Transactional
    public CheckinResult checkin(String userId, CheckinRequest request) {
        String type = request.type() == null ? "" : request.type();
        Double met = MET.get(type);
        if (met == null) {
            throw ApiException.badRequest("运动类型不在可选范围内");
        }

        Double durationValue = request.duration();
        if (durationValue == null
                || !Double.isFinite(durationValue)
                || durationValue != Math.rint(durationValue)
                || durationValue < 1
                || durationValue > 600) {
            throw ApiException.badRequest("时长必须是 1–600 的整数");
        }
        int duration = durationValue.intValue();

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }

        double weight = request.weight() != null
                ? request.weight()
                : user.getWeight() != null ? user.getWeight().doubleValue() : 60;
        if (!Double.isFinite(weight) || weight < 20 || weight > 300) {
            throw ApiException.badRequest("体重需在 20–300 kg 之间");
        }

        LocalDate today = ShanghaiTime.today();
        int calories = (int) Math.round(met * weight * (duration / 60.0));

        CheckinRecord record = new CheckinRecord();
        record.setUserId(userId);
        record.setDate(today);
        record.setType(type);
        record.setDuration(duration);
        record.setCalories(calories);

        try {
            if (Boolean.TRUE.equals(request.overwrite())) {
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
        if (today.equals(lastCheckDate)) {
            streak = currentStreak > 0 ? currentStreak : 1;
        } else if (lastCheckDate != null
                && ChronoUnit.DAYS.between(lastCheckDate, today) == 1) {
            streak = currentStreak + 1;
        }

        try {
            User patch = new User();
            patch.setId(userId);
            patch.setStreak(streak);
            patch.setLastCheckDate(today);
            userMapper.updateById(patch);
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "保存打卡记录失败");
        }

        return new CheckinResult(today, duration, calories, streak);
    }
}
