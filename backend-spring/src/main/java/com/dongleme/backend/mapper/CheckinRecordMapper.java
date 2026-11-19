package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.AdminRecentRow;
import com.dongleme.backend.entity.CheckinRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    @Insert("""
            insert into records (user_id, date, type, duration, calories)
            values (#{userId}, #{date}, #{type}, #{duration}, #{calories})
            on duplicate key update duration = values(duration), calories = values(calories)
            """)
    int upsert(CheckinRecord record);

    @Select("""
            select count(distinct user_id) from records where date = #{date}
            """)
    long countActiveUsers(@Param("date") LocalDate date);

    @Select("""
            select coalesce(sum(duration), 0) from records where date >= #{weekStart}
            """)
    long sumMinutesSince(@Param("weekStart") LocalDate weekStart);

    @Select("""
            select r.id, r.date, r.type, r.duration, r.calories, u.nickname
            from records r
            join users u on u.id = r.user_id
            order by r.created_at desc
            limit 10
            """)
    List<AdminRecentRow> selectRecentRecords();
}
