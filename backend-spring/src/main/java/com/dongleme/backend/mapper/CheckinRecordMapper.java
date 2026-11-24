package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.AdminRecentRow;
import com.dongleme.backend.dto.AdminTrendRow;
import com.dongleme.backend.dto.AdminTypeStatRow;
import com.dongleme.backend.entity.CheckinRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    @Insert("""
            insert into records (user_id, date, type, duration, calories, source, verified, flagged)
            values (#{userId}, #{date}, #{type}, #{duration}, #{calories}, #{source}, #{verified}, #{flagged})
            on duplicate key update
                duration = if(verified = 1 and values(verified) = 0, duration, values(duration)),
                calories = if(verified = 1 and values(verified) = 0, calories, values(calories)),
                source = if(verified = 1 and values(verified) = 0, source, values(source)),
                flagged = if(verified = 1 and values(verified) = 0, flagged, values(flagged)),
                verified = greatest(verified, values(verified))
            """)
    int upsert(CheckinRecord record);

    @Select("""
            select count(distinct user_id) from records where date = #{date}
            """)
    long countActiveUsers(@Param("date") LocalDate date);

    @Select("""
            select count(*) from records where date = #{date}
            """)
    long countByDate(@Param("date") LocalDate date);

    @Select("""
            select coalesce(sum(duration), 0) from records where date >= #{weekStart}
            """)
    long sumMinutesSince(@Param("weekStart") LocalDate weekStart);

    @Select("""
            select date as date, count(*) as records, count(distinct user_id) as users
            from records
            where date >= #{from}
            group by date
            order by date asc
            """)
    List<AdminTrendRow> selectTrend(@Param("from") LocalDate from);

    @Select("""
            select type as name, count(*) as count, coalesce(sum(duration), 0) as minutes
            from records
            group by type
            order by count desc, minutes desc
            """)
    List<AdminTypeStatRow> selectTypeStats();

    @Select("""
            select r.id, r.date, r.type, r.duration, r.calories, u.nickname
            from records r
            join users u on u.id = r.user_id
            order by r.created_at desc
            limit 10
            """)
    List<AdminRecentRow> selectRecentRecords();
}
