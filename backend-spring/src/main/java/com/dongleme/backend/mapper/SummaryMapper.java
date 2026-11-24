package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.entity.WeeklySummary;
import org.apache.ibatis.annotations.Insert;

public interface SummaryMapper extends BaseMapper<WeeklySummary> {

    @Insert("""
            insert into summaries (user_id, week_start, content, plan_goal, regen_count)
            values (#{userId}, #{weekStart}, #{content}, #{planGoal}, #{regenCount})
            on duplicate key update content = values(content),
                                    plan_goal = values(plan_goal),
                                    regen_count = values(regen_count)
            """)
    int upsert(WeeklySummary summary);
}
