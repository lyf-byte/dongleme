package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.AdminUserRow;
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

    @Select("""
            select u.id, u.nickname, u.weight, u.weekly_goal, u.streak,
                   u.is_admin as admin, u.created_at, u.email, u.phone, u.username,
                   count(r.id) as record_count
            from users u
            left join records r on r.user_id = u.id
            group by u.id, u.nickname, u.weight, u.weekly_goal, u.streak,
                     u.is_admin, u.created_at, u.email, u.phone, u.username
            order by u.created_at desc
            limit 500
            """)
    List<AdminUserRow> selectAdminUsers();
}
