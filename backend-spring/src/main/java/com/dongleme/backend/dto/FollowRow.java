package com.dongleme.backend.dto;

import lombok.Data;

@Data
public class FollowRow {

    private String id;

    private String nickname;

    private String remark;

    private String gender;

    private Long weeklyMinutes;
}
