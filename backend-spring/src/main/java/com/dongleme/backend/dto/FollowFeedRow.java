package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class FollowFeedRow {

    private Long id;

    private String userId;

    private String nickname;

    private String remark;

    private String gender;

    private LocalDate date;

    private String type;

    private Integer duration;

    private Integer calories;

    private Integer flagged;
}
