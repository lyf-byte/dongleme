package com.dongleme.backend.mapper;

import com.dongleme.backend.dto.AdminRecordRow;
import com.dongleme.backend.dto.AdminRouteRow;
import com.dongleme.backend.dto.AdminSummaryRow;
import com.dongleme.backend.dto.AdminUserStatsRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 后台聚合查询：把多次往返合并成单条 SQL，降低远程数据库的网络往返耗时。
 */
public interface AdminStatsMapper {

    @Select("""
            select
              (select count(*) from users) as total_users,
              (select count(*) from records) as total_records,
              (select count(distinct user_id) from records where date = #{today}) as active_today,
              (select count(*) from records where date = #{today}) as today_records,
              (select cast(coalesce(sum(d.day_minutes), 0) as signed) from (
                  select r.user_id, r.date,
                         sum(case when r.flagged = 1 then 0 when r.verified = 1 then r.duration else 0 end)
                         + least(sum(case when r.flagged = 1 then 0 when r.verified = 1 then 0 else r.duration end), 240) as day_minutes
                  from records r group by r.user_id, r.date
              ) d where d.date >= #{weekStart}) as week_minutes,
              (select count(*) from users where created_at >= #{weekStart}) as week_new_users,
              (select count(*) from activity_routes) as route_count
            """)
    AdminSummaryRow selectSummary(
            @Param("today") LocalDate today,
            @Param("weekStart") LocalDate weekStart);

    @Select("""
            select
              (select count(*) from records where user_id = #{userId}) as record_count,
              (select cast(coalesce(sum(d.day_minutes), 0) as signed) from (
                  select r.user_id, r.date,
                         sum(case when r.flagged = 1 then 0 when r.verified = 1 then r.duration else 0 end)
                         + least(sum(case when r.flagged = 1 then 0 when r.verified = 1 then 0 else r.duration end), 240) as day_minutes
                  from records r where r.user_id = #{userId} group by r.user_id, r.date
              ) d where d.date >= #{weekStart}) as week_minutes,
              (select count(*) from activity_routes where user_id = #{userId}) as route_count
            """)
    AdminUserStatsRow selectUserStats(
            @Param("userId") String userId,
            @Param("weekStart") LocalDate weekStart);

    @Select("""
            <script>
            select r.id, r.date, r.type, r.duration, r.calories, r.user_id, u.nickname,
                   count(*) over() as total
            from records r
            join users u on u.id = r.user_id
            <where>
              <if test="type != null and type != ''"> and r.type = #{type} </if>
              <if test="from != null"> and r.date &gt;= #{from} </if>
              <if test="to != null"> and r.date &lt;= #{to} </if>
              <if test="query != null and query != ''">
                and u.nickname like concat('%', #{query}, '%')
              </if>
            </where>
            order by r.date desc, r.created_at desc
            limit #{limit} offset #{offset}
            </script>
            """)
    List<AdminRecordRow> selectRecords(
            @Param("query") String query,
            @Param("type") String type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            select count(*)
            from records r
            join users u on u.id = r.user_id
            <where>
              <if test="type != null and type != ''"> and r.type = #{type} </if>
              <if test="from != null"> and r.date &gt;= #{from} </if>
              <if test="to != null"> and r.date &lt;= #{to} </if>
              <if test="query != null and query != ''">
                and u.nickname like concat('%', #{query}, '%')
              </if>
            </where>
            </script>
            """)
    long countRecords(
            @Param("query") String query,
            @Param("type") String type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Select("""
            select r.id, r.date, r.type, r.duration, r.calories, r.user_id, u.nickname,
                   count(*) over() as total
            from records r
            join users u on u.id = r.user_id
            where r.flagged = 1
            order by r.date desc, r.created_at desc
            limit #{limit} offset #{offset}
            """)
    List<AdminRecordRow> selectFlaggedRecords(
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            select count(*) from records where flagged = 1
            """)
    long countFlaggedRecords();

    @Select("""
            <script>
            select a.id, a.user_id, u.nickname, a.type, a.date, a.distance_m, a.duration_sec,
                   a.calories, a.point_count, a.created_at, a.preview,
                   count(*) over() as total
            from activity_routes a
            join users u on u.id = a.user_id
            <where>
              <if test="type != null and type != ''"> and a.type = #{type} </if>
              <if test="from != null"> and a.date &gt;= #{from} </if>
              <if test="to != null"> and a.date &lt;= #{to} </if>
              <if test="query != null and query != ''">
                and u.nickname like concat('%', #{query}, '%')
              </if>
            </where>
            order by a.created_at desc
            limit #{limit} offset #{offset}
            </script>
            """)
    List<AdminRouteRow> selectRoutes(
            @Param("query") String query,
            @Param("type") String type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            select count(*)
            from activity_routes a
            join users u on u.id = a.user_id
            <where>
              <if test="type != null and type != ''"> and a.type = #{type} </if>
              <if test="from != null"> and a.date &gt;= #{from} </if>
              <if test="to != null"> and a.date &lt;= #{to} </if>
              <if test="query != null and query != ''">
                and u.nickname like concat('%', #{query}, '%')
              </if>
            </where>
            </script>
            """)
    long countRoutes(
            @Param("query") String query,
            @Param("type") String type,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
