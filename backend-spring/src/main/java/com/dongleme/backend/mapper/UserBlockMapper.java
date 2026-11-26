package com.dongleme.backend.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserBlockMapper {

    @Select("""
            select count(*) from user_blocks
            where owner_id = #{owner} and target_id = #{target}
            """)
    int count(@Param("owner") String owner, @Param("target") String target);

    @Insert("""
            insert into user_blocks (owner_id, target_id)
            values (#{owner}, #{target})
            on duplicate key update created_at = created_at
            """)
    int block(@Param("owner") String owner, @Param("target") String target);

    @Delete("""
            delete from user_blocks
            where owner_id = #{owner} and target_id = #{target}
            """)
    int unblock(@Param("owner") String owner, @Param("target") String target);
}
