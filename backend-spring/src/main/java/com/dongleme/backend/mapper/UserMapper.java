package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.AdminNewUserRow;
import com.dongleme.backend.dto.AdminUserRow;
import com.dongleme.backend.dto.RankRow;
import com.dongleme.backend.dto.UserSearchRow;
import com.dongleme.backend.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    @Select("select u.id, u.nickname, u.gender, "
            + "cast(coalesce(sum(d.day_minutes), 0) as signed) as weekly_minutes "
            + "from users u "
            + "join (" + RecordMetrics.EFFECTIVE_DAY_MINUTES_SQL + ") d "
            + "on d.user_id = u.id and d.date >= #{weekStart} "
            + "group by u.id, u.nickname, u.gender "
            + "order by weekly_minutes desc, u.nickname asc")
    List<RankRow> selectWeeklyRank(@Param("weekStart") LocalDate weekStart);

    @Select("select u.id, u.nickname, u.gender, "
            + "cast(coalesce(sum(d.day_minutes), 0) as signed) as weekly_minutes "
            + "from users u "
            + "join (" + RecordMetrics.EFFECTIVE_DAY_MINUTES_SQL + ") d on d.user_id = u.id "
            + "group by u.id, u.nickname, u.gender "
            + "order by weekly_minutes desc, u.nickname asc")
    List<RankRow> selectTotalRank();

    /**
     * 昵称模糊搜索；手机号、邮箱、账号名只做完整匹配，避免通过片段枚举账号。
     * 排除自己，以及双方任一方向已拉黑的用户。
     */
    @Select("""
            select u.id, u.nickname, u.gender, cr.remark,
                   (exists(select 1 from follows f
                            where f.follower_id = #{me} and f.followee_id = u.id)) as is_following,
                   (exists(select 1 from follows b
                            where b.follower_id = u.id and b.followee_id = #{me})) as follows_me
            from users u
            left join contact_remarks cr on cr.owner_id = #{me} and cr.target_id = u.id
            where u.id <> #{me}
              and (u.nickname like concat('%', #{keyword}, '%')
                   or u.phone = #{keyword}
                   or u.email = #{keyword}
                   or u.username = #{keyword})
              and not exists (select 1 from user_blocks b1
                              where b1.owner_id = #{me} and b1.target_id = u.id)
              and not exists (select 1 from user_blocks b2
                              where b2.owner_id = u.id and b2.target_id = #{me})
            order by case when u.phone = #{keyword} or u.email = #{keyword}
                               or u.username = #{keyword} then 0 else 1 end,
                     u.nickname asc
            limit 20
            """)
    List<UserSearchRow> searchUsers(
            @Param("me") String me,
            @Param("keyword") String keyword);

    @Select("""
            select count(*) from users where created_at >= #{from}
            """)
    long countNewUsersSince(@Param("from") LocalDateTime from);

    @Select("""
            select date(created_at) as date, count(*) as new_users
            from users
            where created_at >= #{from}
            group by date(created_at)
            order by date asc
            """)
    List<AdminNewUserRow> selectNewUserTrend(@Param("from") LocalDate from);

    @Select("""
            <script>
            select u.id, u.nickname, u.gender, u.weight, u.weekly_goal, u.streak,
                   u.last_check_date, u.is_admin as admin, u.created_at, u.email, u.phone, u.username,
                   count(r.id) as record_count,
                   cast(coalesce((
                       select sum(d.day_minutes) from (
                           select r.user_id, r.date,
                                  sum(case when r.flagged = 1 then 0 when r.verified = 1 then r.duration else 0 end)
                                  + least(sum(case when r.flagged = 1 then 0 when r.verified = 1 then 0 else r.duration end), 240) as day_minutes
                           from records r where r.user_id = u.id group by r.user_id, r.date
                       ) d where d.date &gt;= #{weekStart}
                   ), 0) as signed) as week_minutes,
                   (select count(*) from activity_routes ar where ar.user_id = u.id) as route_count,
                   count(*) over() as total
            from users u
            left join records r on r.user_id = u.id
            <where>
              <if test="query != null and query != ''">
                (u.nickname like concat('%', #{query}, '%')
                 or u.email like concat('%', #{query}, '%')
                 or u.phone like concat('%', #{query}, '%')
                 or u.username like concat('%', #{query}, '%'))
              </if>
              <if test="filter == 'admin'"> and u.is_admin = 1 </if>
              <if test="filter == 'activeToday'">
                and exists (select 1 from records r2 where r2.user_id = u.id and r2.date = #{today})
              </if>
              <if test="filter == 'hasRecords'">
                and exists (select 1 from records r3 where r3.user_id = u.id)
              </if>
              <if test="filter == 'noRecords'">
                and not exists (select 1 from records r4 where r4.user_id = u.id)
              </if>
            </where>
            group by u.id, u.nickname, u.gender, u.weight, u.weekly_goal, u.streak,
                     u.last_check_date, u.is_admin, u.created_at, u.email, u.phone, u.username
            <choose>
              <when test="sort == 'streak'">order by u.streak desc, u.created_at desc</when>
              <when test="sort == 'records'">order by record_count desc, u.created_at desc</when>
              <when test="sort == 'weekMinutes'">order by week_minutes desc, u.created_at desc</when>
              <when test="sort == 'lastCheck'">order by u.last_check_date desc, u.created_at desc</when>
              <otherwise>order by u.created_at desc</otherwise>
            </choose>
            limit #{limit} offset #{offset}
            </script>
            """)
    List<AdminUserRow> selectAdminUsersPage(
            @Param("query") String query,
            @Param("filter") String filter,
            @Param("sort") String sort,
            @Param("today") LocalDate today,
            @Param("weekStart") LocalDate weekStart,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            select count(*) from users u
            <where>
              <if test="query != null and query != ''">
                (u.nickname like concat('%', #{query}, '%')
                 or u.email like concat('%', #{query}, '%')
                 or u.phone like concat('%', #{query}, '%')
                 or u.username like concat('%', #{query}, '%'))
              </if>
              <if test="filter == 'admin'"> and u.is_admin = 1 </if>
              <if test="filter == 'activeToday'">
                and exists (select 1 from records r2 where r2.user_id = u.id and r2.date = #{today})
              </if>
              <if test="filter == 'hasRecords'">
                and exists (select 1 from records r3 where r3.user_id = u.id)
              </if>
              <if test="filter == 'noRecords'">
                and not exists (select 1 from records r4 where r4.user_id = u.id)
              </if>
            </where>
            </script>
            """)
    long countAdminUsers(
            @Param("query") String query,
            @Param("filter") String filter,
            @Param("today") LocalDate today);
}
