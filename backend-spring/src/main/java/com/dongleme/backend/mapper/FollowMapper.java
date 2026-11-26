package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.FollowFeedRow;
import com.dongleme.backend.dto.FollowRow;
import com.dongleme.backend.entity.Follow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {

    @Select("select u.id, u.nickname, cr.remark, u.gender, "
            + "(select cast(coalesce(sum(d.day_minutes), 0) as signed) from ("
            + RecordMetrics.EFFECTIVE_DAY_MINUTES_SQL
            + ") d where d.user_id = u.id and d.date >= #{weekStart}) as weekly_minutes "
            + "from follows f "
            + "join users u on u.id = f.followee_id "
            + "left join contact_remarks cr on cr.owner_id = #{userId} and cr.target_id = u.id "
            + "where f.follower_id = #{userId} "
            + "order by f.created_at desc")
    List<FollowRow> selectFollowing(
            @Param("userId") String userId,
            @Param("weekStart") LocalDate weekStart);

    @Select("""
            select u.id, u.nickname, cr.remark, u.gender
            from follows f
            join follows b on b.follower_id = f.followee_id and b.followee_id = f.follower_id
            join users u on u.id = f.followee_id
            left join contact_remarks cr on cr.owner_id = #{userId} and cr.target_id = u.id
            where f.follower_id = #{userId}
            order by f.created_at desc
            """)
    List<FollowRow> selectMutual(@Param("userId") String userId);

    @Select("""
            select r.id, r.user_id, u.nickname, cr.remark, u.gender,
                   r.date, r.type, r.duration, r.calories, r.flagged
            from follows f
            join records r on r.user_id = f.followee_id
            join users u on u.id = r.user_id
            left join contact_remarks cr on cr.owner_id = #{userId} and cr.target_id = r.user_id
            where f.follower_id = #{userId} and r.date >= #{from}
            order by r.date desc, r.created_at desc
            limit #{limit}
            """)
    List<FollowFeedRow> selectFeed(
            @Param("userId") String userId,
            @Param("from") LocalDate from,
            @Param("limit") int limit);
}
