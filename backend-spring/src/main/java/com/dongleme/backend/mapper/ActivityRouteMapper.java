package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.entity.ActivityRoute;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ActivityRouteMapper extends BaseMapper<ActivityRoute> {

    @Select("""
            select * from activity_routes
            where user_id = #{userId} and client_id = #{clientId}
            limit 1
            """)
    ActivityRoute selectByClientId(
            @Param("userId") String userId,
            @Param("clientId") String clientId);
}
