package com.dongleme.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("activity_routes")
public class ActivityRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    private LocalDate date;

    private String type;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("duration_sec")
    private Integer durationSec;

    @TableField("distance_m")
    private Integer distanceM;

    private Integer calories;

    @TableField("point_count")
    private Integer pointCount;

    private String points;

    /**
     * 列表缩略图用的降采样坐标，JSON 数组 [[lat, lng], ...]。
     */
    private String preview;

    @TableField("start_lat")
    private BigDecimal startLat;

    @TableField("start_lng")
    private BigDecimal startLng;

    @TableField("end_lat")
    private BigDecimal endLat;

    @TableField("end_lng")
    private BigDecimal endLng;

    @TableField("client_id")
    private String clientId;

    private String source;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
