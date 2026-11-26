package com.dongleme.backend.mapper;

import com.dongleme.backend.dto.ConversationSettingRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ConversationSettingMapper {

    @Select("""
            select pinned, muted from conversation_settings
            where owner_id = #{owner} and peer_id = #{peer}
            limit 1
            """)
    ConversationSettingRow select(
            @Param("owner") String owner,
            @Param("peer") String peer);

    @Insert("""
            insert into conversation_settings (owner_id, peer_id, pinned, muted)
            values (#{owner}, #{peer}, #{pinned}, #{muted})
            on duplicate key update pinned = values(pinned), muted = values(muted), updated_at = now()
            """)
    int upsert(
            @Param("owner") String owner,
            @Param("peer") String peer,
            @Param("pinned") boolean pinned,
            @Param("muted") boolean muted);
}
