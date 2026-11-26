package com.dongleme.backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface UserReportMapper {

    @Insert("""
            insert into user_reports (reporter_id, target_id, reason)
            values (#{reporter}, #{target}, #{reason})
            """)
    int insert(
            @Param("reporter") String reporter,
            @Param("target") String target,
            @Param("reason") String reason);
}
