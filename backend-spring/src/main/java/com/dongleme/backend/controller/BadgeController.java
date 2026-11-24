package com.dongleme.backend.controller;

import com.dongleme.backend.common.ApiException;
import com.dongleme.backend.dto.BadgeUnlockRequest;
import com.dongleme.backend.security.AuthUser;
import com.dongleme.backend.service.BadgeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser principal) {
        List<String> ids = badgeService.ids(requireId(principal));
        return Map.of("ids", ids);
    }

    @PostMapping
    public Map<String, Object> unlock(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) BadgeUnlockRequest request) {
        List<String> ids = badgeService.unlock(requireId(principal), request);
        return Map.of("ids", ids);
    }

    private static String requireId(AuthUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return principal.id();
    }
}
