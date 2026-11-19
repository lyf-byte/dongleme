package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.RankRow;
import com.dongleme.backend.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    @Select("""
            select u.id, u.nickname, coalesce(sum(r.duration), 0) as weekly_minutes
            from users u
            join records r on r.user_id = u.id and r.date >= #{weekStart}
            group by u.id, u.nickname
            order by weekly_minutes desc, u.nickname asc
            """)
    List<RankRow> selectWeeklyRank(@Param("weekStart") LocalDate weekStart);
}
