package com.dongleme.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 用户搜索结果：只返回展示所需字段，不返回手机号与邮箱。
 */
@Data
public class UserSearchRow {

    private String id;

    private String nickname;

    private String gender;

    private String remark;

    @JsonProperty("isFollowing")
    private Boolean isFollowing;

    private Boolean followsMe;
}
