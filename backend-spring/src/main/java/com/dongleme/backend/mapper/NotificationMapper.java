package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.NotificationRow;
import com.dongleme.backend.entity.Notification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("""
            select n.id, n.type, n.actor_id, u.nickname, cr.remark, u.gender,
                   n.content, n.read_at, n.created_at
            from notifications n
            left join users u on u.id = n.actor_id
            left join contact_remarks cr
                   on cr.owner_id = #{me} and cr.target_id = n.actor_id
            where n.user_id = #{me}
            order by n.id desc
            limit #{limit}
            """)
    List<NotificationRow> selectForUser(@Param("me") String me, @Param("limit") int limit);

    @Select("""
            select count(*) from notifications
            where user_id = #{me} and read_at is null
            """)
    long countUnread(@Param("me") String me);

    @Select("""
            select count(*) from notifications
            where user_id = #{me} and actor_id = #{actor}
              and type = 'follow' and read_at is null
            """)
    long countUnreadFollowFrom(@Param("me") String me, @Param("actor") String actor);

    @Update("""
            update notifications set read_at = now()
            where user_id = #{me} and id = #{id} and read_at is null
            """)
    int markRead(@Param("me") String me, @Param("id") long id);

    @Update("""
            update notifications set read_at = now()
            where user_id = #{me} and read_at is null
            """)
    int markAllRead(@Param("me") String me);
}
