package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.FollowFeedRow;
import com.dongleme.backend.dto.FollowRequest;
import com.dongleme.backend.dto.FollowRow;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.FollowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping
    public List<FollowRow> following(@AuthenticationPrincipal AuthUser principal) {
        return followService.following(requireId(principal));
    }

    @GetMapping("/feed")
    public List<FollowFeedRow> feed(@AuthenticationPrincipal AuthUser principal) {
        return followService.feed(requireId(principal));
    }

    @PostMapping
    public Map<String, Object> follow(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) FollowRequest request) {
        return followService.follow(requireId(principal), request == null ? null : request.userId());
    }

    @DeleteMapping
    public Map<String, Object> unfollow(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(name = "userId", required = false) String userId) {
        return followService.unfollow(requireId(principal), userId);
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
