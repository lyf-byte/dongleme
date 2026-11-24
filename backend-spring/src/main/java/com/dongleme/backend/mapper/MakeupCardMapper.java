package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.entity.MakeupCard;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface MakeupCardMapper extends BaseMapper<MakeupCard> {

    @Select("""
            select count(*) from makeup_cards where user_id = #{userId} and used_at >= #{from}
            """)
    int countUsedSince(@Param("userId") String userId, @Param("from") LocalDateTime from);
}
