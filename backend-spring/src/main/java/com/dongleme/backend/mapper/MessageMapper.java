package com.dongleme.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongleme.backend.dto.ConversationRow;
import com.dongleme.backend.entity.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MessageMapper extends BaseMapper<Message> {

    @Select("""
            select u.id as user_id, u.nickname, cr.remark, u.gender,
                   case when last.recalled_at is not null then '消息已撤回'
                        when last.type = 'image' then '[图片]'
                        when last.type = 'record' then '[运动卡片]'
                        when last.type = 'location' then '[位置]'
                        else last.content end as last_content,
                   last.created_at as last_at,
                   (select count(*) from messages um
                     where um.sender_id = u.id and um.receiver_id = #{me}
                       and um.read_at is null and um.receiver_deleted = 0) as unread,
                   (exists(select 1 from follows f1
                            where f1.follower_id = #{me} and f1.followee_id = u.id)
                    and exists(select 1 from follows f2
                            where f2.follower_id = u.id and f2.followee_id = #{me})) as mutual,
                   coalesce(cs.pinned, 0) as pinned,
                   coalesce(cs.muted, 0) as muted
            from (
                select case when m.sender_id = #{me} then m.receiver_id else m.sender_id end as peer_id,
                       max(m.id) as last_id
                from messages m
                where (m.sender_id = #{me} and m.sender_deleted = 0)
                   or (m.receiver_id = #{me} and m.receiver_deleted = 0)
                group by peer_id
            ) t
            join messages last on last.id = t.last_id
            join users u on u.id = t.peer_id
            left join contact_remarks cr
                   on cr.owner_id = #{me} and cr.target_id = u.id
            left join conversation_settings cs
                   on cs.owner_id = #{me} and cs.peer_id = u.id
            order by pinned desc, last.created_at desc
            """)
    List<ConversationRow> selectConversations(@Param("me") String me);

    @Select("""
            select *
            from messages
            where ((sender_id = #{me} and receiver_id = #{peer} and sender_deleted = 0)
                or (sender_id = #{peer} and receiver_id = #{me} and receiver_deleted = 0))
              and (#{beforeId} is null or id < #{beforeId})
            order by id desc
            limit #{limit}
            """)
    List<Message> selectHistory(
            @Param("me") String me,
            @Param("peer") String peer,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    @Select("""
            select count(*) from messages
            where receiver_id = #{me} and read_at is null and receiver_deleted = 0
            """)
    long countUnreadAll(@Param("me") String me);

    @Select("""
            select count(*) from messages where media_url = #{url}
            """)
    long countByMediaUrl(@Param("url") String url);

    @Update("""
            update messages set read_at = now()
            where sender_id = #{peer} and receiver_id = #{me}
              and read_at is null and receiver_deleted = 0
            """)
    int markReadFrom(@Param("me") String me, @Param("peer") String peer);

    @Select("""
            select count(*) from messages
            where sender_id = #{me} and receiver_id = #{peer}
              and created_at >= (now() - interval 10 second)
            """)
    long countRecentSent(@Param("me") String me, @Param("peer") String peer);
}
