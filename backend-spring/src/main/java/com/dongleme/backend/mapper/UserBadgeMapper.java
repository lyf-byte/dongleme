package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.entity.UserBadge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserBadgeMapper extends BaseMapper<UserBadge> {

    @Insert("""
            <script>
            insert ignore into user_badges (user_id, badge_id)
            values
            <foreach collection="badgeIds" item="badgeId" separator=",">
              (#{userId}, #{badgeId})
            </foreach>
            </script>
            """)
    int insertIgnore(@Param("userId") String userId, @Param("badgeIds") List<String> badgeIds);
}
