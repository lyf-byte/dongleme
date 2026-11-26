package com.dongleme.backend.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ContactRemarkMapper {

    @Select("""
            select remark from contact_remarks
            where owner_id = #{ownerId} and target_id = #{targetId}
            limit 1
            """)
    String selectRemark(
            @Param("ownerId") String ownerId,
            @Param("targetId") String targetId);

    @Insert("""
            insert into contact_remarks (owner_id, target_id, remark)
            values (#{ownerId}, #{targetId}, #{remark})
            on duplicate key update remark = values(remark), updated_at = now()
            """)
    int upsert(
            @Param("ownerId") String ownerId,
            @Param("targetId") String targetId,
            @Param("remark") String remark);

    @Delete("""
            delete from contact_remarks
            where owner_id = #{ownerId} and target_id = #{targetId}
            """)
    int delete(
            @Param("ownerId") String ownerId,
            @Param("targetId") String targetId);
}
