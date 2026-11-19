package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.entity.CheckinRecord;
import org.apache.ibatis.annotations.Insert;

public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    @Insert("""
            insert into records (user_id, date, type, duration, calories)
            values (#{userId}, #{date}, #{type}, #{duration}, #{calories})
            on duplicate key update duration = values(duration), calories = values(calories)
            """)
    int upsert(CheckinRecord record);
}
