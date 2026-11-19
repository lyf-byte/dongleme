package com.dongleme.backend.service;

import com.dongleme.backend.common.ShanghaiTime;
import com.dongleme.backend.dto.RankItem;
import com.dongleme.backend.dto.RankMe;
import com.dongleme.backend.dto.RankRow;
import com.dongleme.backend.dto.RankView;
import com.dongleme.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RankService {

    private static final int TOP_SIZE = 20;

    private final UserMapper userMapper;

    public RankService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public RankView getRank(String userId, String scopeParam) {
        boolean total = "total".equals(scopeParam);
        List<RankRow> rows = total
                ? userMapper.selectTotalRank()
                : userMapper.selectWeeklyRank(ShanghaiTime.weekStart(ShanghaiTime.today()));

        List<RankItem> all = new ArrayList<>(rows.size());
        int meIndex = -1;

        for (int index = 0; index < rows.size(); index++) {
            RankRow row = rows.get(index);
            String nickname = row.getNickname() == null || row.getNickname().isBlank()
                    ? "运动新人"
                    : row.getNickname();
            long minutes = row.getWeeklyMinutes() == null ? 0 : row.getWeeklyMinutes();
            boolean isMe = row.getId().equals(userId);
            if (isMe) {
                meIndex = index;
            }
            all.add(new RankItem(row.getId(), nickname, minutes, isMe));
        }

        RankMe me = null;
        if (meIndex >= 0) {
            long mine = all.get(meIndex).weeklyMinutes();
            long gap = meIndex > 0 ? all.get(meIndex - 1).weeklyMinutes() - mine : 0;
            me = new RankMe(meIndex + 1, mine, gap);
        }

        return new RankView(all.stream().limit(TOP_SIZE).toList(), me);
    }
}
