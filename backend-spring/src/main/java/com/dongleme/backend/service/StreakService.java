package com.dongleme.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dongleme.backend.entity.CheckinRecord;
import com.dongleme.backend.entity.MakeupCard;
import com.dongleme.backend.entity.User;
import com.dongleme.backend.mapper.CheckinRecordMapper;
import com.dongleme.backend.mapper.MakeupCardMapper;
import com.dongleme.backend.mapper.UserMapper;
import com.dongleme.backend.security.UserLookupCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 连续天数重算：打卡记录与补签日期合并后，从最近一天向前数连续天数。
 */
@Service
public class StreakService {

    private final CheckinRecordMapper recordMapper;
    private final MakeupCardMapper makeupCardMapper;
    private final UserMapper userMapper;
    private final UserLookupCache userLookupCache;

    public StreakService(
            CheckinRecordMapper recordMapper,
            MakeupCardMapper makeupCardMapper,
            UserMapper userMapper,
            UserLookupCache userLookupCache) {
        this.recordMapper = recordMapper;
        this.makeupCardMapper = makeupCardMapper;
        this.userMapper = userMapper;
        this.userLookupCache = userLookupCache;
    }

    @Transactional
    public int recalculate(String userId) {
        Set<LocalDate> dates = new HashSet<>();
        recordMapper.selectList(
                        Wrappers.<CheckinRecord>lambdaQuery()
                                .eq(CheckinRecord::getUserId, userId))
                .forEach(row -> {
                    if (row.getDate() != null) {
                        dates.add(row.getDate());
                    }
                });
        makeupCardMapper.selectList(
                        Wrappers.<MakeupCard>lambdaQuery()
                                .eq(MakeupCard::getUserId, userId))
                .forEach(row -> {
                    if (row.getTargetDate() != null) {
                        dates.add(row.getTargetDate());
                    }
                });

        if (dates.isEmpty()) {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, userId)
                    .set(User::getStreak, 0)
                    .set(User::getLastCheckDate, null));
            userLookupCache.invalidate(userId);
            return 0;
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
        return streak;
    }
}
