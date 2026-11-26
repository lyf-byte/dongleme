package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.UserSearchRow;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.FollowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户搜索：昵称模糊匹配，手机号 / 邮箱 / 账号名精确匹配，用于查找好友。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final FollowService followService;

    public UserController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping("/search")
    public List<UserSearchRow> search(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(name = "keyword", required = false) String keyword) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return followService.searchUsers(principal.id(), keyword);
    }
}
